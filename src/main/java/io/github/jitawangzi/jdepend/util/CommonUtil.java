package io.github.jitawangzi.jdepend.util;

import java.util.List;
import java.util.Set;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;

import io.github.jitawangzi.jdepend.config.AppConfig;

public class CommonUtil {

	/**
	 * 获取当前类的全限定名（包含包名）
	 * @param cu 编译单元对象
	 * @return 全限定类名（如：com.example.MyClass）
	 */
	public static String getFullClassName(CompilationUnit cu) {
		// 获取包声明
		String packageName = cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");

		// 获取主类名（简单名称）
		String className = cu.getPrimaryTypeName().filter(name -> !name.isEmpty()).orElseGet(() -> {
			// 查找第一个类或接口声明
			List<ClassOrInterfaceDeclaration> declarations = cu.findAll(ClassOrInterfaceDeclaration.class);
			if (!declarations.isEmpty()) {
				return declarations.get(0).getNameAsString();
			}
			// 尝试从文件名推断
			return cu.getStorage()
					.map(storage -> storage.getFileName().replace(".java", ""))
					.orElseThrow(() -> new IllegalStateException("无法获取类名"));
		});

		// 组合包名和类名
		return packageName.isEmpty() ? className : packageName + "." + className;
	}

	public static boolean isSingleLineMethod(MethodDeclaration method) {
		if (method.getBody().isPresent()) {
			BlockStmt body = method.getBody().get();
			// 获取方法体开始和结束行
			int beginLine = body.getBegin().get().line;
			int endLine = body.getEnd().get().line;
			// 实际代码行数(减去花括号占用的行)
			int codeLines = endLine - beginLine - 1;
			return codeLines == 1;
		}
		return false;
	}

	/**
	 * 判断是否是项目内的类
	 * 
	 * @param className 类名
	 * @return 是否是项目内的类
	 */
	public static boolean isProjectClass(String className) {
		Set<String> projectPackagePrefixes = AppConfig.INSTANCE.getProjectPackagePrefixes();
		boolean ret = false;
		for (String prefix : projectPackagePrefixes) {
			if (className.startsWith(prefix)) {
				ret = true;
			}
		}
		if (!ret) {
			return false;
		}
		return !isExcludedPackage(className);
	}

	/**
	 * 判断是否是被排除的包
	 * 
	 * @param className 类名
	 * @return 是否被排除
	 */
	public static boolean isExcludedPackage(String className) {
		return AppConfig.INSTANCE.getExcludedPackages().stream().anyMatch(className::startsWith);
	}
}
