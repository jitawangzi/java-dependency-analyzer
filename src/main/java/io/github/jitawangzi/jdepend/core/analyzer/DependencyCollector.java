package io.github.jitawangzi.jdepend.core.analyzer;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;

import io.github.jitawangzi.jdepend.config.AppConfig;
import io.github.jitawangzi.jdepend.core.model.ClassDependency;
import io.github.jitawangzi.jdepend.util.FileLocator;

/**
 * 依赖收集器，负责收集类的依赖关系
 */
public class DependencyCollector {
	private final Map<String, Integer> classDepths = new HashMap<>();
	private final Set<String> collected = new HashSet<>();
	private final FileLocator fileLocator;

	/**
	 * 构造函数
	 * 
	 * @param config 配置对象
	 */
	public DependencyCollector() {
		this.fileLocator = new FileLocator();
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
		Set<String> projectImports = getProjectImports(className);
		for (String importClass : projectImports) {
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
				|| isExcludedPackage(className);
	}

	/**
	 * 判断是否是被排除的包
	 * 
	 * @param className 类名
	 * @return 是否被排除
	 */
	private boolean isExcludedPackage(String className) {
		return AppConfig.INSTANCE.getExcludedPackages().stream().anyMatch(className::startsWith);
	}

	/**
	 * 获取项目中的导入类
	 * 
	 * @param className 类名
	 * @return 导入的类集合
	 * @throws Exception 如果获取过程中发生错误
	 */
	private Set<String> getProjectImports(String className) throws Exception {
		Path file = fileLocator.locate(className);
		if (file == null)
			return Collections.emptySet();

		// 解析Java文件
		CompilationUnit cu = StaticJavaParser.parse(file);
		return cu.getImports().stream().map(ImportDeclaration::getNameAsString).filter(this::isProjectClass).collect(Collectors.toSet());
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