package io.github.jitawangzi.jdepend.core.analyzer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import io.github.jitawangzi.jdepend.config.AppConfig;
import io.github.jitawangzi.jdepend.core.analyzer.JavaMethodCallAnalyzer.MethodCallInfo;
import io.github.jitawangzi.jdepend.core.model.MethodReferenceInfo;
import io.github.jitawangzi.jdepend.util.CommonUtil;
import io.github.jitawangzi.jdepend.util.FileLocator;

/**
 * 方法级依赖分析器 - 分析实际方法调用来确定依赖关系
 * 使用延迟分析模式，只在需要时分析类
 */
public class MethodDependencyAnalyzer {
	private static Logger log = LoggerFactory.getLogger(MethodDependencyAnalyzer.class);

	/** 类---- depth */
	private final Map<String, Integer> allDependencies = new HashMap<String, Integer>();
	private final Set<String> analyzedClasses = new HashSet<>();
	private final Queue<ClassAnalysisTask> pendingClasses = new LinkedList<>();

	// 方法调用依赖映射（方法到类的映射）key:完整类名+方法名，value:这个方法调用到的类集合
	private Map<String, Set<String>> methodDependencies = new HashMap<>();

	// 方法调用依赖映射（方法到方法的映射）key:完整类名+方法名 ,value:这个方法调用到的其他方法（完整类名+方法名）集合
	private Map<String, Set<String>> methodToMethodDependencies = new HashMap<>();

	// 方法引用信息，key是方法全名（类名.方法名），value是引用信息
	private Map<String, MethodReferenceInfo> methodReferences = new HashMap<>();

	// 跟踪从主类开始的实际调用路径
	private Set<String> reachableMethods = new HashSet<>();

	/**
	 * 表示待分析的类任务
	 */
	private static class ClassAnalysisTask {
		private final String className;
		private final int depth;

		public ClassAnalysisTask(String className, int depth) {
			this.className = className;
			this.depth = depth;
		}

		public String getClassName() {
			return className;
		}

		public int getDepth() {
			return depth;
		}
	}

	/**
	 * 构造函数
	 */
	public MethodDependencyAnalyzer() {
	}

	/**
	 * 分析类的所有可能依赖，但只包含实际被调用的类
	 * 
	 * @param startClass 起始类
	 * @return 实际依赖的类集合
	 * @throws IOException 如果分析过程中发生IO错误
	 */
	public Set<String> analyzeAllDependencies(String startClass) throws IOException {
		long timeMillis = System.currentTimeMillis();
		log.info("开始从类 {} 分析依赖...", startClass);

		// 添加起始类到待分析队列
		pendingClasses.add(new ClassAnalysisTask(startClass, 0));

		// 处理所有待分析类，直到队列为空
		processClassQueue();

		log.info("共分析了 {} 个依赖类,耗时 {} ms", analyzedClasses.size(), (System.currentTimeMillis() - timeMillis));

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
	 * 处理待分析类队列
	 * 
	 * @throws IOException 如果分析过程中发生IO错误
	 */
	private void processClassQueue() throws IOException {
		while (!pendingClasses.isEmpty()) {
			ClassAnalysisTask task = pendingClasses.poll();
			String className = task.getClassName();
			int depth = task.getDepth();

			// 跳过已分析的类、排除的包，以及超出深度限制的类
			if (analyzedClasses.contains(className) || CommonUtil.isExcludedPackage(className) || !CommonUtil.isProjectClass(className)
					|| (AppConfig.INSTANCE.getMaxDepth() > 0 && depth > AppConfig.INSTANCE.getMaxDepth())) {
				continue;
			}

			// 分析这个类
			analyzeClass(className, depth);
		}
	}

	/**
	 * 分析单个类及其方法调用
	 * 
	 * @param className 类名
	 * @param depth 当前深度
	 * @throws IOException 如果分析过程中发生IO错误
	 */
	private void analyzeClass(String className, int depth) throws IOException {
		// 标记该类已分析
		analyzedClasses.add(className);
		allDependencies.put(className, depth);

		// 解析类文件
		CompilationUnit cu = CommonUtil.parseCompilationUnit(className);

		boolean keepMethods = CommonUtil.shouldKeepMethods(className, depth);
		if (keepMethods) {// 只有在保留方法体的时候，才需要处理引用的其他类，否则可以忽略
			// 收集类级别依赖（导入、接口、父类等）
			Set<String> collectClassLevelDependencies = CommonUtil.collectClassLevelDependencies(cu, className);
			for (String string : collectClassLevelDependencies) {
				if (CommonUtil.isProjectClass(string) && !allDependencies.containsKey(string)) {
					allDependencies.put(string, depth + 1);
					pendingClasses.add(new ClassAnalysisTask(string, depth + 1));
				}
			}
		}
		// 分析方法调用
		analyzeMethodCallsForClass(cu, className, depth);
	}

	/**
	 * 分析单个类文件的方法调用
	 * 
	 * @param classFile 类文件路径
	 * @param className 类名
	 */
	private void analyzeMethodCallsForClass(CompilationUnit cu, String className, int depth) {
		try {
			// 分析类中的方法调用
			Map<String, MethodCallInfo> methodCalls = JavaMethodCallAnalyzer.analyzeJavaFile(cu);

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

						// 调用类的全限定名
						String calledClass = scope;
						// 如果是当前类的方法调用
						if (scope.equals("this")) {
							calledClass = className;
						}

						if (CommonUtil.isProjectClass(calledClass)) {
							String calledMethod = calledClass + "." + methodName;
							methodDependencies.get(callerMethod).add(calledClass);
							methodToMethodDependencies.get(callerMethod).add(calledMethod);

							// 更新方法引用信息
							methodReferences.putIfAbsent(calledMethod, new MethodReferenceInfo(calledClass, methodName));
							methodReferences.get(calledMethod).addCaller(callerMethod);

							// 将被调用的类添加到待分析队列
							if (!analyzedClasses.contains(calledClass)) {
//								pendingClasses.add(new ClassAnalysisTask(calledClass, 0)); // 重置深度，因为这是直接调用
								int nextDepth = allDependencies.containsKey(calledClass) ? allDependencies.get(calledClass) : depth + 1;
								pendingClasses.add(new ClassAnalysisTask(calledClass, nextDepth));
							}
						} else {
							log.debug("跳过非项目类的调用: {}", calledClass);
						}
					}
				}
			}
		} catch (Exception e) {
			log.error("分析类 {} 的方法调用时出错", className, e);
		}
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
		String simpleMethodName = methodName.substring(methodName.lastIndexOf(".") + 1);
		actualDependencies.add(className);

		// 获取这个方法体里面直接引用的类
		if (methodDependencies.containsKey(methodName)) {
			Set<String> referencedClasses = methodDependencies.get(methodName);
			actualDependencies.addAll(referencedClasses);
		}
		CompilationUnit cu = CommonUtil.parseCompilationUnit(className);
		if (cu == null) {
			log.warn("无法解析类 {} 的编译单元，跳过方法依赖分析", className);
			return;
		}
		cu.findAll(MethodDeclaration.class).forEach(method -> {
			if (method.getNameAsString().equals(simpleMethodName)) {
				// 获取这个方法参数、返回值等引用的类
				CommonUtil.collectDependenciesFromMethod(actualDependencies, method);
			}
		});
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
			Path file = FileLocator.getInstance().locate(className);
			if (file != null) {
				CompilationUnit cu = CommonUtil.parseCompilationUnit(className);
				// 添加接口和父类
				cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
					// 添加实现的接口
					for (ClassOrInterfaceType implementedType : classDecl.getImplementedTypes()) {
						for (ImportDeclaration importDecl : cu.getImports()) {
							String importName = importDecl.getNameAsString();
							if (importName.endsWith("." + implementedType.getNameAsString())) {
								if (CommonUtil.isProjectClass(importName) && !dependencies.contains(importName)) {
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
								if (CommonUtil.isProjectClass(importName) && !dependencies.contains(importName)) {
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
	 * 分析多个类的实际依赖
	 * 
	 * @param classes 要分析的类集合
	 * @return 实际依赖的类集合
	 * @throws IOException 如果分析过程中发生IO错误
	 */
	public Set<String> analyzeAllDependenciesForClasses(Set<String> classes) throws IOException {
		log.info("开始分析多个类的依赖关系，共 {} 个类", classes.size());

		// 将所有类添加到待分析队列
		for (String className : classes) {
			pendingClasses.add(new ClassAnalysisTask(className, 0));
		}

		// 处理所有待分析类
		processClassQueue();

		// 计算实际依赖
		return calculateActualDependencies(classes);
	}

	/**
	 * 计算指定入口类的实际依赖
	 * 
	 * @param entryClasses 入口类集合
	 * @return 实际依赖的类集合
	 */
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

	public Map<String, Set<String>> getMethodDependencies() {
		return methodDependencies;
	}

	public Map<String, Set<String>> getMethodToMethodDependencies() {
		return methodToMethodDependencies;
	}

}