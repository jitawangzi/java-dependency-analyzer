package io.github.jitawangzi.jdepend.core.processor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

import io.github.jitawangzi.jdepend.config.AppConfig;
import io.github.jitawangzi.jdepend.config.RuntimeConfig;

/**
 * 方法过滤器，用于移除未被引用的方法
 */
public class MethodFilter {
	private static Logger log = LoggerFactory.getLogger(MethodFilter.class);

	private final Set<String> reachableMethods;
	private final String mainClassName;

	// 存储每个类中被移除的方法
	private final Map<String, List<MethodDeclaration>> removedMethods = new HashMap<>();

	/**
	 * 构造函数
	 * 
	 * @param config 配置对象
	 * @param reachableMethods 可达方法集合
	 * @param mainClassName 主类名
	 */
	public MethodFilter(Set<String> reachableMethods, String mainClassName) {
		this.reachableMethods = reachableMethods;
		this.mainClassName = mainClassName;

		// 调试信息
		log.debug("可达方法总数: {}", reachableMethods.size());
	}

	/**
	 * 过滤类中未被引用的方法
	 * 
	 * @param cu 编译单元
	 * @param className 类名
	 */
	public void filterUnreferencedMethods(CompilationUnit cu, String className) {
		if (!RuntimeConfig.isDirectoryMode && !AppConfig.INSTANCE.isKeepOnlyReferencedMethods() || className.equals(mainClassName)) {
			// 如果不启用过滤或者是主类，跳过处理
			return;
		}

		// 处理类中的方法
		cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
			List<MethodDeclaration> methodsToRemove = new ArrayList<>();
			int totalMethods = classDecl.getMethods().size();

			// 首先标识所有需要移除的方法
			for (MethodDeclaration method : classDecl.getMethods()) {
				String methodName = method.getNameAsString();
				String fullMethodName = className + "." + methodName;

				// 检查方法是否可达
				boolean isReachable = reachableMethods.contains(fullMethodName);

				// 如果方法没有被引用，且不是特殊方法，标记为移除
				if (!isReachable && !isSpecialMethod(method, classDecl)) {
					methodsToRemove.add(method);
					log.debug("标记未引用方法: {}", fullMethodName);
				} else {
					log.debug("保留方法: {} ({}方法)", fullMethodName, isReachable ? "可达" : "特殊方法");
				}
			}

			// 记录需要移除的方法
			if (!methodsToRemove.isEmpty()) {
				// 克隆方法用于记录
				List<MethodDeclaration> clonedMethods = methodsToRemove.stream().map(MethodDeclaration::clone).collect(Collectors.toList());

				removedMethods.put(className, clonedMethods);

				// 然后从AST中移除这些方法
				for (MethodDeclaration method : methodsToRemove) {
					classDecl.remove(method);
				}

				log.debug("类 {} 共有 {} 个方法，移除了 {} 个未引用方法，保留 {} 个方法", className, totalMethods, methodsToRemove.size(),
						(totalMethods - methodsToRemove.size()));
			} else {
				log.debug("类 {} 的所有方法都被保留", className);
			}
		});
	}

	/**
	 * 判断方法是否是特殊方法（构造函数、main方法等）
	 * 
	 * @param method 方法声明
	 * @param classDecl 类声明
	 * @return 是否是特殊方法
	 */
	private boolean isSpecialMethod(MethodDeclaration method, ClassOrInterfaceDeclaration classDecl) {
		String methodName = method.getNameAsString();

		// 构造函数总是保留
		if (methodName.equals(classDecl.getNameAsString())) {
			return true;
		}

		// main方法总是保留
		if (methodName.equals("main") && method.isStatic() && method.getParameters().size() == 1
				&& method.getParameter(0).getType().asString().equals("String[]")) {
			return true;
		}

		// 重写的Object方法总是保留
		if (method.isAnnotationPresent("Override") || methodName.equals("equals") || methodName.equals("hashCode")
				|| methodName.equals("toString")) {
			return true;
		}

		return false;
	}

	/**
	 * 获取被移除的方法
	 * 
	 * @return 被移除的方法映射
	 */
	public Map<String, List<MethodDeclaration>> getRemovedMethods() {
		return removedMethods;
	}
}