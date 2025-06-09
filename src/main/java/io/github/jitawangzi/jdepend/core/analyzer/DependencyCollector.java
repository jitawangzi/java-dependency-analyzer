package io.github.jitawangzi.jdepend.core.analyzer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.jitawangzi.jdepend.config.AppConfig;
import io.github.jitawangzi.jdepend.core.model.ClassDependency;
import io.github.jitawangzi.jdepend.util.CommonUtil;

/**
 * 依赖收集器，负责收集类的依赖关系
 */
public class DependencyCollector {
	private final Map<String, Integer> classDepths = new HashMap<>();
	private final Set<String> collected = new HashSet<>();

	/**
	 * 构造函数
	 * 
	 * @param config 配置对象
	 */
	public DependencyCollector() {
	}

	/**
	 * 收集依赖
	 * 
	 * @return 依赖列表
	 * @throws Exception 如果收集过程中发生错误
	 */
	public List<ClassDependency> collect() throws Exception {
		collectDependencies(AppConfig.INSTANCE.getMainClass(), 0);
		return classDepths.entrySet().stream().map(e -> new ClassDependency(e.getKey(), e.getValue())).collect(Collectors.toList());
	}

	/**
	 * 收集一个类的依赖
	 * 
	 * @param className 类名
	 * @param currentDepth 当前深度
	 * @throws Exception 如果收集过程中发生错误
	 */
	private void collectDependencies(String className, int currentDepth) throws Exception {
		if (shouldSkip(className, currentDepth))
			return;

		classDepths.put(className, currentDepth);
		collected.add(className);
		Set<String> classes = CommonUtil.collectClassLevelDependencies(CommonUtil.parseCompilationUnit(className), className);
		for (String importClass : classes) {
			int nextDepth = currentDepth + 1;
			if (!classDepths.containsKey(importClass) || classDepths.get(importClass) > nextDepth) {
				collectDependencies(importClass, nextDepth);
			}
		}
	}

	/**
	 * 判断是否应该跳过这个类
	 * 
	 * @param className 类名
	 * @param currentDepth 当前深度
	 * @return 是否应该跳过
	 */
	private boolean shouldSkip(String className, int currentDepth) {
		return collected.contains(className) || (AppConfig.INSTANCE.getMaxDepth() > 0 && currentDepth > AppConfig.INSTANCE.getMaxDepth())
				|| CommonUtil.isExcludedPackage(className);
	}


	public List<ClassDependency> collectFromClasses(Set<String> classes) throws Exception {
		classes.forEach(className -> {
			try {
				collectDependencies(className, 0);
			} catch (Exception e) {
				System.err.println("Error analyzing class: " + className);
			}
		});
		return convertToDependencyList();
	}

	private List<ClassDependency> convertToDependencyList() {
		return classDepths.entrySet()
				.stream()
				.map(entry -> new ClassDependency(entry.getKey(), entry.getValue()))
				.collect(Collectors.toList());
	}
}