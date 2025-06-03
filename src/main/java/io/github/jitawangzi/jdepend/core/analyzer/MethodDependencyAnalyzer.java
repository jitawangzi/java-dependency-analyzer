package io.github.jitawangzi.jdepend.core.analyzer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import io.github.jitawangzi.jdepend.config.AppConfig;
import io.github.jitawangzi.jdepend.core.model.MethodReferenceInfo;
import io.github.jitawangzi.jdepend.util.FileLocator;
import io.github.jitawangzi.jdepend.util.JavaMethodCallAnalyzer;
import io.github.jitawangzi.jdepend.util.JavaMethodCallAnalyzer.MethodCallInfo;

/**
 * 方法级依赖分析器 - 分析实际方法调用来确定依赖关系
 */
public class MethodDependencyAnalyzer {
	private static Logger log = LoggerFactory.getLogger(MethodDependencyAnalyzer.class);

	private final FileLocator fileLocator;
	private final Set<String> allDependencies = new HashSet<>();
	private final Set<String> analyzedClasses = new HashSet<>();

	// 方法调用依赖映射（方法到类的映射）
	private Map<String, Set<String>> methodDependencies = new HashMap<>();

	// 方法调用依赖映射（方法到方法的映射）
	private Map<String, Set<String>> methodToMethodDependencies = new HashMap<>();

	// 方法引用信息，key是方法全名（类名.方法名），value是引用信息
	private Map<String, MethodReferenceInfo> methodReferences = new HashMap<>();

	// 跟踪从主类开始的实际调用路径
	private Set<String> reachableMethods = new HashSet<>();

	/**
	 * 构造函数
	 * 
	 * @param config 配置对象
	 */
	public MethodDependencyAnalyzer() {
		this.fileLocator = new FileLocator();
	}

	/**
	 * 分析类的所有可能依赖，但只包含实际被调用的类
	 * 
	 * @param startClass 起始类
	 * @return 实际依赖的类集合
	 * @throws IOException 如果分析过程中发生IO错误
	 */
	public Set<String> analyzeAllDependencies(String startClass) throws IOException {
		// 首先收集所有潜在依赖
		analyzeClassDependencies(startClass, 0);
		log.info("共分析了 {} 个潜在依赖类", analyzedClasses.size());
		// 使用JavaMethodCallAnalyzer分析方法调用
		analyzeMethodCalls();

		// 从主类开始计算可达方法
		calculateReachableMethods(startClass);

		// 从主类开始递归追踪实际方法调用依赖
		Set<String> actualDependencies = new HashSet<>();
		Set<String> analyzedMethods = new HashSet<>();

		// 添加主类自身
		actualDependencies.add(startClass);

		// 从主类的所有方法开始分析
		for (String methodName : methodToMethodDependencies.keySet()) {
			if (methodName.startsWith(startClass + ".")) {
				analyzeMethodDependenciesRecursively(methodName, actualDependencies, analyzedMethods);
			}
		}

		// 添加必要的接口和父类依赖
		Set<String> finalDependencies = new HashSet<>(actualDependencies);
		for (String className : actualDependencies) {
			addEssentialDependencies(className, finalDependencies);
		}

		log.info("实际依赖分析完成，从 {} 个潜在依赖中筛选出 {} 个实际依赖", allDependencies.size(), finalDependencies.size());
		return finalDependencies;
	}

	/**
	 * 计算从主类开始可达的所有方法
	 * 
	 * @param startClass 主类名
	 */
	private void calculateReachableMethods(String startClass) {
		log.debug("开始计算从主类 {} 可达的方法...", startClass);
		// 首先把主类的所有方法标记为可达
		for (String methodName : methodToMethodDependencies.keySet()) {
			if (methodName.startsWith(startClass + ".")) {
				reachableMethods.add(methodName);
				log.debug("添加主类方法: {}", methodName);
			}
		}

		// 特殊处理：如果我们没有分析到主类中的某些方法（如私有方法），
		// 我们可以尝试添加构造函数和常用方法
		reachableMethods.add(startClass + ".main");
		reachableMethods.add(startClass + "." + getSimpleClassName(startClass)); // 构造函数

		// 从主类方法开始，递归标记所有可达方法
		Set<String> visitedMethods = new HashSet<>();
		for (String methodName : new HashSet<>(reachableMethods)) {
			markReachableMethods(methodName, visitedMethods);
		}

		log.debug("从主类 {} 可达的方法分析完成，共找到 {} 个可达方法", startClass, reachableMethods.size());
		// 调试输出所有可达方法
		log.debug("可达方法列表:");
		reachableMethods.stream().sorted().forEach(log::debug);
	}

	/**
	 * 从类全名中获取简单类名
	 */
	private String getSimpleClassName(String fullClassName) {
		int lastDot = fullClassName.lastIndexOf('.');
		if (lastDot > 0) {
			return fullClassName.substring(lastDot + 1);
		}
		return fullClassName;
	}

	/**
	 * 递归标记可达方法
	 * 
	 * @param methodName 方法名
	 * @param visitedMethods 已访问的方法集合
	 */
	private void markReachableMethods(String methodName, Set<String> visitedMethods) {
		if (visitedMethods.contains(methodName)) {
			return;
		}

		visitedMethods.add(methodName);
		reachableMethods.add(methodName);

		// 获取该方法调用的所有方法
		if (methodToMethodDependencies.containsKey(methodName)) {
			Set<String> calledMethods = methodToMethodDependencies.get(methodName);
			for (String calledMethod : calledMethods) {
				log.debug("方法 {} 调用了 {}", methodName, calledMethod);
				markReachableMethods(calledMethod, visitedMethods);
			}
		}
	}

	/**
	 * 分析单个类的所有潜在依赖
	 * 
	 * @param className 类名
	 * @param depth 当前深度
	 * @throws IOException 如果分析过程中发生IO错误
	 */
	private void analyzeClassDependencies(String className, int depth) throws IOException {
		// 防止循环依赖和超出深度限制
		if (analyzedClasses.contains(className) || isExcludedPackage(className)
				|| (AppConfig.INSTANCE.getMaxDepth() > 0 && depth > AppConfig.INSTANCE.getMaxDepth())) {
			return;
		}

		// 标记该类已分析
		analyzedClasses.add(className);
		allDependencies.add(className);

		// 查找类文件
		Path file = fileLocator.locate(className);
		if (file == null) {
			log.warn("找不到类文件: {}", className);
			return;
		}

		// 解析类文件
		String source = Files.readString(file);
		CompilationUnit cu = StaticJavaParser.parse(source);

		// 1. 从导入语句中收集依赖
		for (ImportDeclaration importDecl : cu.getImports()) {
			String importName = importDecl.getNameAsString();
			if (isProjectClass(importName)) {
				// 检查是否是通配符导入 (例如: import com.example.*)
				if (importDecl.isAsterisk()) {
					// 通配符导入处理
					String packageName = importDecl.getNameAsString();
					// TODO: 这里可以进一步处理通配符导入，但目前我们只是记录日志
					log.warn("发现通配符导入: {}.*  ，暂时没有处理这种依赖", packageName);
				} else {
					// 具体类导入处理
					allDependencies.add(importName);
					// 递归分析导入的类
					analyzeClassDependencies(importName, depth + 1);
				}

			}
		}

		// 2. 查找接口和父类
		cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
			// 收集实现的接口
			for (ClassOrInterfaceType implementedType : classDecl.getImplementedTypes()) {
				String typeName = implementedType.getNameAsString();
				for (ImportDeclaration importDecl : cu.getImports()) {
					String importName = importDecl.getNameAsString();
					if (importName.endsWith("." + typeName)) {
						if (isProjectClass(importName)) {
							allDependencies.add(importName);
							try {
								analyzeClassDependencies(importName, depth + 1);
							} catch (IOException e) {
								log.error("无法分析接口: {}", importName, e);
							}
						}
					}
				}
			}

			// 收集继承的类
			for (ClassOrInterfaceType extendedType : classDecl.getExtendedTypes()) {
				String typeName = extendedType.getNameAsString();
				for (ImportDeclaration importDecl : cu.getImports()) {
					String importName = importDecl.getNameAsString();
					if (importName.endsWith("." + typeName)) {
						if (isProjectClass(importName)) {
							allDependencies.add(importName);
							try {
								analyzeClassDependencies(importName, depth + 1);
							} catch (IOException e) {
								log.error("无法分析父类: {}", importName, e);
							}
						}
					}
				}
			}
		});

		// 3. 从字段声明中收集依赖
		cu.findAll(FieldDeclaration.class).forEach(field -> {
			field.getVariables().forEach(var -> {
				String fieldTypeName = var.getType().asString();
				// 查找匹配的导入
				for (ImportDeclaration importDecl : cu.getImports()) {
					String importName = importDecl.getNameAsString();
					if (importName.endsWith("." + fieldTypeName) || (fieldTypeName.contains("<")
							&& importName.endsWith("." + fieldTypeName.substring(0, fieldTypeName.indexOf("<"))))) {
						if (isProjectClass(importName)) {
							allDependencies.add(importName);
							try {
								analyzeClassDependencies(importName, depth + 1);
							} catch (IOException e) {
								log.error("无法分析字段类型: {}", importName, e);
							}
						}
					}
				}

				// 检查字段类型中的泛型参数
				if (fieldTypeName.contains("<") && fieldTypeName.contains(">")) {
					String genericPart = fieldTypeName.substring(fieldTypeName.indexOf("<") + 1, fieldTypeName.lastIndexOf(">"));
					for (String typePart : genericPart.split(",")) {
						typePart = typePart.trim();
						for (ImportDeclaration importDecl : cu.getImports()) {
							String importName = importDecl.getNameAsString();
							if (importName.endsWith("." + typePart)) {
								if (isProjectClass(importName)) {
									allDependencies.add(importName);
									try {
										analyzeClassDependencies(importName, depth + 1);
									} catch (IOException e) {
										log.error("无法分析泛型参数: {}", importName, e);
									}
								}
							}
						}
					}
				}
			});
		});
	}

	/**
	 * 使用JavaMethodCallAnalyzer分析所有类的方法调用关系
	 * 
	 * @throws IOException 如果分析过程中发生IO错误
	 */
	private void analyzeMethodCalls() throws IOException {
		// 准备源码目录列表
		List<String> sourceDirs = new ArrayList<>();
		sourceDirs.add(AppConfig.INSTANCE.getProjectRootPath());

		log.info("开始分析方法调用关系...");

		// 分析每个已发现的类
		for (String className : analyzedClasses) {
			Path classFile = fileLocator.locate(className);
			if (classFile != null) {
				try {
					// 分析类中的方法调用
					Map<String, MethodCallInfo> methodCalls = JavaMethodCallAnalyzer.analyzeJavaFile(classFile.toString(), sourceDirs);

					// 处理分析结果
					for (MethodCallInfo info : methodCalls.values()) {
						String callerMethod = className + "." + info.getMethodName();

						// 初始化依赖集合
						methodDependencies.putIfAbsent(callerMethod, new HashSet<>());
						methodToMethodDependencies.putIfAbsent(callerMethod, new HashSet<>());

						// 处理被调用的方法
						for (Map.Entry<String, List<JavaMethodCallAnalyzer.MethodCall>> entry : info.getMethodCalls().entrySet()) {
							for (JavaMethodCallAnalyzer.MethodCall call : entry.getValue()) {
								String scope = call.getScope();
								String methodName = call.getMethodName();

								// 如果是当前类的方法调用
								if (scope.equals("this")) {
									String calledMethod = className + "." + methodName;
									methodDependencies.get(callerMethod).add(className);
									methodToMethodDependencies.get(callerMethod).add(calledMethod);

									// 更新方法引用信息
									methodReferences.putIfAbsent(calledMethod, new MethodReferenceInfo(className, methodName));
									methodReferences.get(calledMethod).addCaller(callerMethod);
								} else {
									// 尝试确定被调用类的全限定名
									String calledClass = resolveClassName(scope, className);
									if (calledClass != null) {
										String calledMethod = calledClass + "." + methodName;
										methodDependencies.get(callerMethod).add(calledClass);
										methodToMethodDependencies.get(callerMethod).add(calledMethod);

										// 更新方法引用信息
										methodReferences.putIfAbsent(calledMethod, new MethodReferenceInfo(calledClass, methodName));
										methodReferences.get(calledMethod).addCaller(callerMethod);
									}
								}
							}
						}
					}
				} catch (Exception e) {
					log.error("分析类 {} 的方法调用时出错", className, e);
				}
			}
		}

		log.info("方法调用关系分析完成，共分析 {} 个方法", methodToMethodDependencies.size());
	}

	/**
	 * 解析类名的全限定名
	 * 
	 * @param scope 作用域
	 * @param currentClass 当前类
	 * @return 全限定类名，如果找不到则返回null
	 */
	private String resolveClassName(String scope, String currentClass) {
		String className;

		// 处理方法调用表达式
		if (scope.contains("(")) {
			int parenIndex = scope.indexOf("(");
			String expression = scope.substring(0, parenIndex);
			int lastDot = expression.lastIndexOf(".");
			if (lastDot > 0) {
				// 提取类名部分
				className = expression.substring(0, lastDot);
			} else {
				className = expression; // 没有点号，整个表达式可能就是类名
			}
		} else if (scope.contains(".")) {
			// 处理带点号的表达式，可能是 package.Class 或 Class.staticField
			int lastDot = scope.lastIndexOf(".");
			className = scope.substring(0, lastDot);
		} else {
			// 简单名称
			className = scope;
		}

		// 现在我们有了类名（可能是简单名称，也可能是部分限定名称）
		// 需要在所有依赖中查找匹配的全限定类名

		// 如果类名本身已经是全限定名，直接返回
		if (allDependencies.contains(className)) {
			return className;
		}

		// 在所有依赖中查找匹配的类名
		for (String dependency : allDependencies) {
			if (dependency.endsWith("." + className)) {
				return dependency;
			}
		}

		// 如果找不到匹配的类，尝试使用当前包
		int lastDot = currentClass.lastIndexOf(".");
		if (lastDot > 0) {
			String currentPackage = currentClass.substring(0, lastDot);
			String possibleClass = currentPackage + "." + className;
			if (allDependencies.contains(possibleClass)) {
				return possibleClass;
			}
		}

		// 找不到匹配的类，返回null或原始值
		return null;
	}
	/**
	 * 递归分析方法依赖
	 * 
	 * @param methodName 方法名
	 * @param actualDependencies 实际依赖集合
	 * @param analyzedMethods 已分析的方法集合
	 */
	private void analyzeMethodDependenciesRecursively(String methodName, Set<String> actualDependencies, Set<String> analyzedMethods) {
		// 如果已经分析过这个方法，跳过
		if (analyzedMethods.contains(methodName)) {
			return;
		}

		analyzedMethods.add(methodName);

		// 获取方法所在的类
		String className = methodName.substring(0, methodName.lastIndexOf("."));
		actualDependencies.add(className);

		// 获取这个方法直接引用的类
		if (methodDependencies.containsKey(methodName)) {
			Set<String> referencedClasses = methodDependencies.get(methodName);
			actualDependencies.addAll(referencedClasses);
		}

		// 获取这个方法调用的所有方法
		if (methodToMethodDependencies.containsKey(methodName)) {
			Set<String> calledMethods = methodToMethodDependencies.get(methodName);
			// 递归分析被调用的方法
			for (String calledMethod : calledMethods) {
				if (!analyzedMethods.contains(calledMethod)) {
					analyzeMethodDependenciesRecursively(calledMethod, actualDependencies, analyzedMethods);
				}
			}
		}
	}

	/**
	 * 添加必要的依赖（接口、父类等）
	 * 
	 * @param className 类名
	 * @param dependencies 依赖集合
	 */
	private void addEssentialDependencies(String className, Set<String> dependencies) {
		try {
			Path file = fileLocator.locate(className);
			if (file != null) {
				String source = Files.readString(file);
				CompilationUnit cu = StaticJavaParser.parse(source);
				// 添加接口和父类
				cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
					// 添加实现的接口
					for (ClassOrInterfaceType implementedType : classDecl.getImplementedTypes()) {
						for (ImportDeclaration importDecl : cu.getImports()) {
							String importName = importDecl.getNameAsString();
							if (importName.endsWith("." + implementedType.getNameAsString())) {
								if (isProjectClass(importName) && !dependencies.contains(importName)) {
									dependencies.add(importName);
									// 递归添加接口的依赖
									addEssentialDependencies(importName, dependencies);
								}
							}
						}
					}

					// 添加继承的类
					for (ClassOrInterfaceType extendedType : classDecl.getExtendedTypes()) {
						for (ImportDeclaration importDecl : cu.getImports()) {
							String importName = importDecl.getNameAsString();
							if (importName.endsWith("." + extendedType.getNameAsString())) {
								if (isProjectClass(importName) && !dependencies.contains(importName)) {
									dependencies.add(importName);
									// 递归添加父类的依赖
									addEssentialDependencies(importName, dependencies);
								}
							}
						}
					}
				});

			}
		} catch (Exception e) {
			log.error("添加必要依赖时出错", e);
		}
	}

	/**
	 * 判断是否是项目内的类
	 * 
	 * @param className 类名
	 * @return 是否是项目内的类
	 */
	private boolean isProjectClass(String className) {
		return className.startsWith("cn.game") && !isExcludedPackage(className);
	}

	/**
	 * 判断是否是被排除的包
	 * 
	 * @param className 类名
	 * @return 是否是被排除的包
	 */
	private boolean isExcludedPackage(String className) {
		return AppConfig.INSTANCE.getExcludedPackages().stream().anyMatch(className::startsWith);
	}

	/**
	 * 返回所有依赖
	 * 
	 * @return 所有依赖的集合
	 */
	public Set<String> getAllDependencies() {
		return allDependencies;
	}

	/**
	 * 获取方法引用信息
	 * 
	 * @return 方法引用信息
	 */
	public Map<String, MethodReferenceInfo> getMethodReferences() {
		return methodReferences;
	}

	/**
	 * 获取从主类可达的方法
	 * 
	 * @return 可达方法集合
	 */
	public Set<String> getReachableMethods() {
		return reachableMethods;
	}

	public Set<String> analyzeAllDependenciesForClasses(Set<String> classes) throws IOException {
		classes.forEach(className -> {
			try {
				analyzeClassDependencies(className, 0);
			} catch (IOException e) {
				log.error("无法分析类: " + className, e);
			}
		});
		analyzeMethodCalls();
		return calculateActualDependencies(classes);
	}

	private Set<String> calculateActualDependencies(Set<String> entryClasses) {
		Set<String> actualDependencies = new HashSet<>();
		Set<String> analyzedMethods = new HashSet<>();

		// 从所有入口类开始分析
		entryClasses.forEach(entryClass -> {
			// 添加入口类本身
			actualDependencies.add(entryClass);

			// 收集所有相关方法
			methodToMethodDependencies.keySet()
					.stream()
					.filter(m -> m.startsWith(entryClass + "."))
					.forEach(method -> analyzeMethodDependenciesRecursively(method, actualDependencies, analyzedMethods));
		});

		// 添加必要的接口和父类依赖
		Set<String> finalDependencies = new HashSet<>(actualDependencies);
		actualDependencies.forEach(className -> addEssentialDependencies(className, finalDependencies));

		return finalDependencies;
	}
}