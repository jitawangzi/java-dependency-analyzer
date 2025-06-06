package io.github.jitawangzi.jdepend.util;

import java.io.File;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import io.github.jitawangzi.jdepend.config.AppConfig;
import io.github.jitawangzi.jdepend.project.ProjectParser;
import io.github.jitawangzi.jdepend.project.ProjectParserFactory;

public class JavaParserInit {
    private static Logger log = LoggerFactory.getLogger(JavaParserInit.class);

	public static void init() {
		try {
			// 设置jdk版本语法
			ParserConfiguration config = new ParserConfiguration();
			config.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
			StaticJavaParser.setConfiguration(config);

			// 1. 创建类型解析器 (TypeSolver)
			CombinedTypeSolver typeSolver = new CombinedTypeSolver();

			// 主项目的根路径
			String projectRootPath = AppConfig.INSTANCE.getProjectRootPath();
			File projectRoot = new File(projectRootPath);

			// 获取适用于当前项目的解析器
			ProjectParser projectParser = ProjectParserFactory.getParser(projectRoot);
			if (projectParser == null) {
				throw new IllegalStateException("Cannot determine project type for: " + projectRootPath);
			}

			log.info("Using project parser: " + projectParser.getClass().getSimpleName());

			// 添加项目及其所有模块的源码路径
			projectParser.addSourceDirectories(typeSolver, projectRoot);

			// 添加我们的极简XML解析器（优先级最高）
			typeSolver.add(new SimpleXmlTypeSolver());

			// 添加标准的JDK类解析器
			typeSolver.add(new ReflectionTypeSolver());

			// 解析项目依赖并添加到 TypeSolver
			List<File> jars = projectParser.resolveDependencies(projectRoot);
			for (File jar : jars) {
				try {
					typeSolver.add(new JarTypeSolver(jar));
				} catch (Exception e) {
					log.warn("Could not add jar to typesolver: {}", jar.getPath(), e);
				}
			}

			// 2. 配置 SymbolSolver
			JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
			StaticJavaParser.getParserConfiguration().setSymbolResolver(symbolSolver);
		} catch (Exception e) {
			log.error("初始化JavaParser失败: ", e);
			System.exit(1);
		}
	}
    public static void main(String[] args) throws Exception {
        init();
        // 解析Java文件
        File sourceFile = new File(
//                "C:\\work_all\\work\\server\\game\\src\\main\\java\\cn\\game\\games\\net\\game\\module\\develop\\pet\\PetHandler.java");
				"C:\\work_all\\work\\server\\protocol\\src\\main\\java\\cn\\game\\protocol\\generated\\config\\GlobalConst.java");
        CompilationUnit cu = StaticJavaParser.parse(sourceFile);
        // 遍历类和方法并解析方法调用
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
            System.out.println("类: " + clazz.getName());
            clazz.findAll(MethodDeclaration.class).forEach(method -> {
                System.out.println("  方法: " + method.getName());
                method.findAll(MethodCallExpr.class).forEach(methodCall -> {
                    try {
                        ResolvedMethodDeclaration resolvedMethod = methodCall.resolve();
                        String classsName = resolvedMethod.getPackageName() + "." + resolvedMethod.getClassName();
                        String methodName = resolvedMethod.getName();
                        System.out.println("    调用了: " + classsName + "." + methodName);
                    } catch (Exception e) {
                        System.out.println("    无法解析方法调用: " + methodCall);
                        e.printStackTrace();
                    }
                });
            });
        });
    }
}
