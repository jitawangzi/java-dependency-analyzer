package io.github.jitawangzi.jdepend.core.processor;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.printer.DefaultPrettyPrinter;

import io.github.jitawangzi.jdepend.config.AppConfigManager;
import io.github.jitawangzi.jdepend.config.ImportConfig;
import io.github.jitawangzi.jdepend.config.RuntimeConfig;
import io.github.jitawangzi.jdepend.util.CommonUtil;

/**
 * 内容处理器，负责处理类的源代码
 */
public class ContentProcessor {
	private static Logger log = LoggerFactory.getLogger(ContentProcessor.class);

	private final BeanMethodProcessor beanMethodProcessor;
	private final MethodFilter methodFilter;

	/**
	 * 构造函数
	 * 
	 * @param config 配置对象
	 * @param importConfig 导入配置对象
	 * @param reachableMethods 可达方法集合
	 */
	public ContentProcessor(Set<String> reachableMethods) {
		this.beanMethodProcessor = new BeanMethodProcessor();
		this.methodFilter = new MethodFilter(reachableMethods, AppConfigManager.get().getMainClass());
	}

	 
    /**
	 * 处理类的源代码
	 * 
	 * @param cu 编译单元
	 * @param sourceCode 源代码
	 * @param depth 依赖深度，一般在类分析模式下使用，用来判断是否需要简化方法体
	 * @return 处理后的源代码
	 */
	public String process(CompilationUnit cu, String sourceCode, int depth) {
        try {
			if (cu == null) {
				cu = StaticJavaParser.parse(sourceCode);
			}
			String className = CommonUtil.getFullClassName(cu);
            // 处理导入语句
            processImports(cu);
            
            // 先过滤未被引用的方法 - 这一步必须在处理JavaBean方法之前
			// 只有在类分析模式下才进行未引用方法过滤，目录模式不过滤，全部保存
			if (!RuntimeConfig.isDirectoryMode && AppConfigManager.get().isKeepOnlyReferencedMethods()) {
                methodFilter.filterUnreferencedMethods(cu, className);
            }
            
            // 然后处理JavaBean方法
			if (AppConfigManager.get().isOmitBeanMethods()) {
                beanMethodProcessor.process(cu, className);
            }
            
//			boolean isMainClass = className.equals(AppConfigManager.get().getMainClass());
			boolean keepMethods = CommonUtil.shouldKeepMethods(className, depth);
            
            if (!keepMethods) {
                // 处理剩余方法体（简化方法实现）
                processMethodBodies(cu);
            }
            
            return new DefaultPrettyPrinter().print(cu);
        } catch (Exception e) {
			log.error("处理类失败: " + sourceCode, e);
            return sourceCode;
        }
    }
    

	/**
	 * 简化方法体实现
	 * 
	 * @param cu 编译单元
	 */
	private static void processMethodBodies(CompilationUnit cu) {
		cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
			classDecl.getMethods().forEach(method -> {
				if (method.getBody().isPresent()) {

					boolean singleLineMethod = CommonUtil.isSingleLineMethod(method);
					if (!singleLineMethod) {
						BlockStmt newBody = new BlockStmt();
						// 对于非void方法添加return语句
						if (!method.getType().isVoidType()) {
							addDefaultReturn(newBody, method.getType().toString());
						}
						// 直接添加注释到方法体
						newBody.addOrphanComment(new LineComment(" Implementation details omitted"));

						method.setBody(newBody);
					}

				}
			});

			classDecl.getConstructors().forEach(constructor -> {
				BlockStmt newBody = new BlockStmt();
				constructor.findFirst(ExplicitConstructorInvocationStmt.class).ifPresent(newBody::addStatement);
				// 直接添加注释
				newBody.addOrphanComment(new LineComment(" Implementation details omitted"));
				constructor.setBody(newBody);
			});
		});
	}

	/**
	 * 添加默认的return语句
	 * 
	 * @param body 方法体
	 * @param returnType 返回类型
	 */
	private static void addDefaultReturn(BlockStmt body, String returnType) {
		try {
			String returnStmt;
			switch (returnType) {
			case "boolean":
				returnStmt = "return false;";
				break;
			case "int":
			case "long":
			case "short":
			case "byte":
				returnStmt = "return 0;";
				break;
			case "double":
			case "float":
				returnStmt = "return 0.0;";
				break;
			case "char":
				returnStmt = "return '\\0';";
				break;
			default:
				// 对象类型和泛型
				returnStmt = "return null;";
				break;
			}

			Statement stmt = StaticJavaParser.parseStatement(returnStmt);
			body.addStatement(stmt);
		} catch (ParseProblemException e) {
			System.err.println("生成默认return语句失败: " + returnType);
		}
	}

	/**
	 * 处理导入语句，根据配置移除不需要的导入
	 * 
	 * @param cu 编译单元
	 */
	private static void processImports(CompilationUnit cu) {
		cu.getImports().removeIf(imp -> {
			String importName = imp.getNameAsString();
			if (imp.isStatic()) {
				return false;
			}
			return !shouldKeepImport(importName);
		});
	}

	/**
	 * 判断是否需要保留该导入
	 */
	private static boolean shouldKeepImport(String importName) {
		if (importName.startsWith("static ")) {
			return true;
		}

		if (ImportConfig.INSTANCE.getKeepPrefixes().stream().anyMatch(importName::startsWith)) {
			return true;
		}

		if (ImportConfig.INSTANCE.isSkipEnabled() && ImportConfig.INSTANCE.getSkipPrefixes().stream().anyMatch(importName::startsWith)) {
			return false;
		}
		return true;
	}

	/**
	 * 获取被省略的访问器方法
	 * 
	 * @return 被省略的访问器方法映射
	 */
	public Map<String, List<MethodDeclaration>> getOmittedAccessors() {
		return beanMethodProcessor.getOmittedAccessors();
	}

	/**
	 * 获取被移除的未引用方法
	 * 
	 * @return 被移除的未引用方法映射
	 */
	public Map<String, List<MethodDeclaration>> getRemovedUnreferencedMethods() {
		return methodFilter.getRemovedMethods();
	}
}