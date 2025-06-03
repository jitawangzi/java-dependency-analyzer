package io.github.jitawangzi.jdepend.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

/**
 * Java方法调用分析工具类
 * 用于分析Java类文件中的方法调用关系
 */
public class JavaMethodCallAnalyzer {

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
	 * @param javaFilePath 待分析的Java文件路径
	 * @param sourceDirs 源码目录路径列表（用于解析依赖）
	 * @return 方法调用信息的Map，key为调用方法名，value为方法调用信息
	 * @throws IOException 如果文件读取异常
	 */
	public static Map<String, MethodCallInfo> analyzeJavaFile(String javaFilePath, List<String> sourceDirs) throws IOException {
		// 初始化符号解析器
		initSymbolSolver(sourceDirs);

		// 解析Java文件
		File sourceFile = new File(javaFilePath);
		CompilationUnit cu = StaticJavaParser.parse(sourceFile);

		// 获取类名和包名
		String className = cu.getPrimaryTypeName().orElse("");
		String packageName = cu.getPackageDeclaration().map(pd -> pd.getName().asString()).orElse("");

		// 收集方法调用信息
		Map<String, MethodCallInfo> methodCallsMap = new HashMap<>();

		// 使用访问者模式分析方法调用
		new VoidVisitorAdapter<Void>() {
			@Override
			public void visit(MethodCallExpr methodCall, Void arg) {
				super.visit(methodCall, arg);
				try {
					// 获取调用者方法
					String callerMethodName = methodCall.findAncestor(com.github.javaparser.ast.body.MethodDeclaration.class)
							.map(md -> md.getNameAsString())
							.orElse("unknown");

					// 获取被调用的方法信息
					String calledMethod = methodCall.getNameAsString();
					String scope = determineScope(methodCall, className);

					// 更新方法调用信息
					methodCallsMap.putIfAbsent(callerMethodName, new MethodCallInfo(callerMethodName));
					MethodCallInfo info = methodCallsMap.get(callerMethodName);
					info.setClassName(className);
					info.setPackageName(packageName);
					info.addMethodCall(scope, calledMethod);
				} catch (Exception e) {
					System.err.println("处理方法调用时出错: " + methodCall);
				}
			}

			/**
			 * 确定方法调用的作用域
			 */
			private String determineScope(MethodCallExpr methodCall, String currentClassName) {

//				try {
//					// 使用符号解析获取方法声明的类
//					ResolvedMethodDeclaration resolvedMethod = methodCall.resolve();
//					ResolvedClassDeclaration declaringClass = resolvedMethod.getDeclaringClass();
//					// 处理内部类（将 $ 转换为 .）
//					return declaringClass.getQualifiedName().replace("$", ".");
//				} catch (Exception e) {
//					// 符号解析失败时回退到语法分析
//					if (methodCall.getScope().isPresent()) {
//						Expression scopeExpr = methodCall.getScope().get();
//						String scopeStr = scopeExpr.toString();
//
//						// 处理链式调用（如 foo.bar().baz()）
//						if (scopeExpr instanceof MethodCallExpr) {
//							return determineScope((MethodCallExpr) scopeExpr, currentClassName);
//						}
//
//						// 提取简单类名（假设作用域是静态类名）
//						if (scopeStr.contains(".")) {
//							return scopeStr.substring(0, scopeStr.lastIndexOf('.'));
//						}
//						return scopeStr;
//					}
//					return "this";
//				}

				// 1. 检查是否有明确的作用域
				if (methodCall.getScope().isPresent()) {
					String scopeStr = methodCall.getScope().get().toString();
					// 如果作用域是"this"，返回当前类名
					if (scopeStr.equals("this")) {
						return currentClassName;
					}
					return scopeStr;
				}

				// 2. 检查是否在lambda表达式中
				Optional<LambdaExpr> lambdaAncestor = methodCall.findAncestor(LambdaExpr.class);
				if (lambdaAncestor.isPresent()) {
					// 检查方法是否是当前类中的方法
					try {
						boolean isClassMethod = cu.findAll(com.github.javaparser.ast.body.MethodDeclaration.class)
								.stream()
								.anyMatch(md -> md.getNameAsString().equals(methodCall.getNameAsString()));
						if (isClassMethod) {
							// 这是一个类方法调用
							return currentClassName;
						}
					} catch (Exception e) {
						// 无法确定，忽略异常
					}
				}

				// 3. 尝试从导入或类定义中确定
				try {
					// 这是简化版，实际上需要更复杂的逻辑来检查导入和字段
					boolean hasSameClassMethod = cu.findAll(com.github.javaparser.ast.body.MethodDeclaration.class)
							.stream()
							.anyMatch(md -> md.getNameAsString().equals(methodCall.getNameAsString()));
					if (hasSameClassMethod) {
						return currentClassName;
					}
				} catch (Exception e) {
					// 无法确定，忽略异常
				}

				// 4. 默认情况
				// 使用"this"表示当前类
				return "this";
			}
		}.visit(cu, null);

		return methodCallsMap;
	}

	/**
	 * 初始化符号解析器
	 * 
	 * @param sourceDirs 源码目录路径列表
	 */
	private static void initSymbolSolver(List<String> sourceDirs) {
		CombinedTypeSolver typeSolver = new CombinedTypeSolver();
		// 添加Java标准库解析器
		typeSolver.add(new ReflectionTypeSolver());

		// 添加项目源码路径解析器
		for (String sourceDir : sourceDirs) {
			try {
				typeSolver.add(new JavaParserTypeSolver(new File(sourceDir)));
			} catch (Exception e) {
				System.err.println("添加源码路径 " + sourceDir + " 时出错: " + e.getMessage());
			}
		}

		// 设置符号解析器
		JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
		StaticJavaParser.getConfiguration().setSymbolResolver(symbolSolver);
	}

	/**
	 * 查找目录下的所有Java文件
	 * 
	 * @param directoryPath 目录路径
	 * @return Java文件路径列表
	 * @throws IOException 如果目录读取异常
	 */
	private static List<String> findJavaFiles(String directoryPath) throws IOException {
		try (Stream<Path> paths = Files.walk(Paths.get(directoryPath))) {
			return paths.filter(Files::isRegularFile)
					.filter(path -> path.toString().endsWith(".java"))
					.map(Path::toString)
					.collect(Collectors.toList());
		}
	}
}