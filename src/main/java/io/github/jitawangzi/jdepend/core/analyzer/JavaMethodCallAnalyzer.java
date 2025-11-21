package io.github.jitawangzi.jdepend.core.analyzer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;

import io.github.jitawangzi.jdepend.config.AppConfigManager;
import io.github.jitawangzi.jdepend.util.CommonUtil;
import io.github.jitawangzi.jdepend.util.ParseUtil;

/**
 * Java方法调用分析工具类
 * 用于分析Java类文件中的方法调用关系
 */
public class JavaMethodCallAnalyzer {
	private static Logger log = LoggerFactory.getLogger(JavaMethodCallAnalyzer.class);
	static AtomicInteger failedCount = new AtomicInteger(0);

	/**
	 * 方法调用信息类，包含调用方法和被调用方法的信息
	 */
	public static class MethodCallInfo {
		// 方法名
		private String methodName;
		// 类名
		private String className;
		// 包名
		private String packageName;
		// 调用的方法集合
		private Map<String, List<MethodCall>> methodCalls;

		public MethodCallInfo(String methodName) {
			this.methodName = methodName;
			this.methodCalls = new HashMap<>();
		}

		public String getMethodName() {
			return methodName;
		}

		public String getClassName() {
			return className;
		}

		public void setClassName(String className) {
			this.className = className;
		}

		public String getPackageName() {
			return packageName;
		}

		public void setPackageName(String packageName) {
			this.packageName = packageName;
		}

		public Map<String, List<MethodCall>> getMethodCalls() {
			return methodCalls;
		}

		public void addMethodCall(String scope, String methodName) {
			methodCalls.putIfAbsent(scope, new ArrayList<>());
			methodCalls.get(scope).add(new MethodCall(scope, methodName));
		}
	}

	/**
	 * 方法调用类，表示一个具体的方法调用
	 */
	public static class MethodCall {
		// 调用作用域（类名）
		private String scope;
		// 方法名
		private String methodName;

		public MethodCall(String scope, String methodName) {
			this.scope = scope;
			this.methodName = methodName;
		}

		public String getScope() {
			return scope;
		}

		public String getMethodName() {
			return methodName;
		}

		@Override
		public String toString() {
			return scope + "." + methodName + "()";
		}
	}

	/**
	 * 分析单个Java文件中的方法调用
	 * 
	 * @param CompilationUnit cu 
	 * @return 方法调用信息的Map，key为调用方法名(类里的方法)，value为方法调用信息（调用了哪些方法）
	 * @throws IOException 如果文件读取异常
	 */
	public static Map<String, MethodCallInfo> analyzeJavaFile(CompilationUnit cu) throws IOException {

		// 获取类名和包名
		String className = CommonUtil.getClassName(cu);
		String packageName = CommonUtil.getPackageName(cu);

		// 收集方法调用信息
		Map<String, MethodCallInfo> methodCallsMap = new HashMap<>();
		// 遍历类和方法并解析方法调用
		cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
			log.debug("类: " + clazz.getName());
			clazz.findAll(MethodDeclaration.class).forEach(method -> {
				log.debug("  方法: " + method.getName());
				method.findAll(MethodCallExpr.class).forEach(methodCall -> {
					try {
						// 获取调用者方法
						String callerMethodName = methodCall.findAncestor(com.github.javaparser.ast.body.MethodDeclaration.class)
								.map(md -> md.getNameAsString())
								.orElse("unknown");
						String resolvedClassName = null;
						String resolvedMethodName = null;
						try {
							ResolvedMethodDeclaration resolvedMethod = methodCall.resolve();
							resolvedClassName = resolvedMethod.getPackageName() + "." + resolvedMethod.getClassName();
							resolvedMethodName = resolvedMethod.getName();
							log.debug("SymbolSolver解析" + className + "中的方法" + callerMethodName + "调用成功: " + resolvedClassName + " "
									+ resolvedMethodName);
						} catch (Exception e) {
							log.debug("SymbolSolver解析" + className + "中的方法" + callerMethodName + "调用失败，退化为自定义方式 ");
							if (AppConfigManager.get().showErrorStacktrace()) {
								log.error("SymbolSolver解析" + className + "中的方法" + callerMethodName + "调用失败,failCount : "
										+ failedCount.incrementAndGet(), e);
							} else {
								log.error("SymbolSolver解析" + className + "中的方法" + callerMethodName + "调用失败,failCount : "
										+ failedCount.incrementAndGet() + ",请检查代码是否有错误");
							}
							// 获取被调用的方法信息
							resolvedMethodName = methodCall.getNameAsString();
							// 正常不应该使用自定义方式，应该全部使用SymbolSolver解析
							resolvedClassName = ParseUtil.determineScope(cu, methodCall);

							log.debug("自定义方式解析" + className + "中的方法" + callerMethodName + "调用结果： " + resolvedClassName + "."
									+ resolvedMethodName);
						}
						if (resolvedMethodName == null || resolvedClassName == null) {
							log.warn("无法解析方法调用: " + methodCall);
							return;
						}
						// 更新方法调用信息
						methodCallsMap.putIfAbsent(callerMethodName, new MethodCallInfo(callerMethodName));
						MethodCallInfo info = methodCallsMap.get(callerMethodName);
						info.setClassName(className);
						info.setPackageName(packageName);
						info.addMethodCall(resolvedClassName, resolvedMethodName);
					} catch (Exception e) {
						if (AppConfigManager.get().showErrorStacktrace()) {
							log.error("解析方法调用失败: " + methodCall, e);
						} else {
							log.error("解析方法调用失败: " + methodCall + ",请检查代码是否有错误");
						}
					}
				});
			});
		});

		return methodCallsMap;
	}

	/**
	 * 在编译单元中查找指定名称的字段
	 * @param cu 编译单元
	 * @param fieldName 字段名称
	 * @return 找到的字段声明（如果存在）
	 */
	private static Optional<FieldDeclaration> findFieldByName(CompilationUnit cu, String fieldName) {
		// 获取当前类中定义的所有字段
		List<FieldDeclaration> fields = new ArrayList<>();

		// 查找主类和内部类的所有字段
		for (TypeDeclaration<?> type : cu.getTypes()) {
			fields.addAll(type.getFields());
		}

		// 查找匹配名称的字段
		for (FieldDeclaration field : fields) {
			for (VariableDeclarator variable : field.getVariables()) {
				if (variable.getNameAsString().equals(fieldName)) {
					return Optional.of(field);
				}
			}
		}

		return Optional.empty();
	}

	/**
	 * 从字段声明中获取类型信息
	 * @param field 字段声明
	 * @return 字段类型的全限定名（尽可能）
	 */
	private static String getTypeFromFieldDeclaration(CompilationUnit cu, FieldDeclaration field) {
		// 获取第一个变量声明器的类型
		com.github.javaparser.ast.type.Type type = field.getVariable(0).getType();
		String typeStr = type.asString();

		// 尝试解析完整类型
		try {
			// 如果有符号解析器，尝试获取完整类型
			if (cu.getSymbolResolver() != null) {
				ResolvedType resolvedType = type.resolve();
				if (resolvedType.isReferenceType()) {
					return resolvedType.asReferenceType().getQualifiedName();
				}
			}
		} catch (Exception e) {
			// 解析失败，使用类型字符串
		}

		// 检查是否是简单类名（需要从导入中查找全限定名）
		if (!typeStr.contains(".") && Character.isUpperCase(typeStr.charAt(0))) {
			// 尝试从导入中找到匹配的类型
			for (ImportDeclaration importDecl : cu.getImports()) {
				String importName = importDecl.getNameAsString();
				if (importName.endsWith("." + typeStr)) {
					return importName;
				}
			}

			// 检查是否在同一包中
			if (cu.getPackageDeclaration().isPresent()) {
				String packageName = cu.getPackageDeclaration().get().getNameAsString();
				// 假设类型在同一包中
				return packageName + "." + typeStr;
			}
		}

		// 对于原始类型或无法解析的类型，直接返回类型字符串
		return typeStr;
	}
}