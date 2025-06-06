package io.github.jitawangzi.jdepend.util;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

@Deprecated
public class MethodReturnTypeResolver {

	/**
	 * 获取引用类的方法的返回类型
	 *
	 * @param cu            被分析的类的编译单元（如 A 类）
	 * @param refClassName  被分析类中引用的目标类的全限定名（如 B 类）
	 * @param refMethodName 被分析类中引用的目标类的方法名（如 print 方法）
	 * @return 引用类的方法的返回类型（全限定名），如果未能解析，返回 Optional.empty()
	 */
	public static Optional<String> findMethodReturnType(CompilationUnit cu, String refClassName, String refMethodName) {
		// 特殊处理：Stream API 的操作
		if (refClassName.equals("java.util.stream.Stream")) {
			return Optional.ofNullable(handleStreamApi(refMethodName));
		}

		// 特殊处理：Collection接口的stream方法
		if (isCollectionClass(refClassName) && refMethodName.equals("stream")) {
			return Optional.of("java.util.stream.Stream");
		}

		// 特殊处理：Collectors类的方法
		if (refClassName.equals("java.util.stream.Collectors")) {
			return handleCollectorsMethods(refMethodName);
		}
		String returnType = ReflectionUtils.getMethodReturnType(refClassName, refMethodName);
		return Optional.of(returnType);
	}

	/**
	 * 处理 Stream API 的方法返回类型
	 *
	 * @param refMethodName 引用的方法名
	 * @return 返回类型的全限定名
	 */
	private static String handleStreamApi(String refMethodName) {
		// 大多数 Stream 操作返回 Stream 本身
		Set<String> streamReturningOps = Set.of("filter", "map", "flatMap", "distinct", "sorted", "peek", "limit", "skip", "takeWhile",
				"dropWhile");
		if (streamReturningOps.contains(refMethodName)) {
			return "java.util.stream.Stream";
		}

		// 特殊 Stream 终端操作
		Map<String, String> terminalOps = Map.of("collect", "java.lang.Object", "toArray", "java.lang.Object[]", "iterator",
				"java.util.Iterator", "spliterator", "java.util.Spliterator", "count", "long", "findFirst", "java.util.Optional", "findAny",
				"java.util.Optional");
		return terminalOps.getOrDefault(refMethodName, "java.lang.Object");
	}

	/**
	 * 处理 Collectors 类的方法返回类型
	 *
	 * @param refMethodName 引用的方法名
	 * @return 方法的返回类型（如果找到）
	 */
	private static Optional<String> handleCollectorsMethods(String refMethodName) {
		Map<String, String> collectorsMethods = Map.of("toList", "java.util.List", "toSet", "java.util.Set", "toMap", "java.util.Map");
		String returnType = collectorsMethods.get(refMethodName);
		return returnType != null ? Optional.of(returnType) : Optional.empty();
	}

	/**
	 * 判断是否为 JDK 类
	 *
	 * @param refClassName 引用类的全限定名
	 * @return 是否为 JDK 类
	 */
	private static boolean isJdkClass(String refClassName) {
		return refClassName.startsWith("java.");
	}


	/**
	 * 判断是否为集合类
	 *
	 * @param refClassName 引用类的全限定名
	 * @return 是否为集合类
	 */
	private static boolean isCollectionClass(String refClassName) {
		return refClassName.startsWith("java.util.") && (refClassName.contains("Collection") || refClassName.contains("List")
				|| refClassName.contains("Set") || refClassName.contains("Map"));
	}

	/**
	 * 在 CompilationUnit 中查找目标类声明
	 *
	 * @param refClassName 引用类的全限定名
	 * @param cu           当前类的 CompilationUnit
	 * @return 目标类的声明（如果找到）
	 */
	private static Optional<ClassOrInterfaceDeclaration> findClassDeclaration(String refClassName, CompilationUnit cu) {
		String simpleClassName = refClassName.substring(refClassName.lastIndexOf(".") + 1);
		return cu.findAll(ClassOrInterfaceDeclaration.class)
				.stream()
				.filter(clazz -> clazz.getNameAsString().equals(simpleClassName))
				.findFirst();
	}

	/**
	 * 递归解析父类或接口中的方法返回类型
	 *
	 * @param cu            编译单元
	 * @param classDecl     当前类声明
	 * @param refMethodName 方法名
	 * @return 方法的返回类型（如果找到）
	 */
	private static Optional<String> resolveFromSuperClassOrInterfaces(CompilationUnit cu, ClassOrInterfaceDeclaration classDecl,
			String refMethodName) {
		// 查找父类
		if (classDecl.getExtendedTypes().isNonEmpty()) {
			String parentClassName = classDecl.getExtendedTypes(0).getNameAsString();
			Optional<ClassOrInterfaceDeclaration> parentClass = findClassDeclaration(parentClassName, cu);
			if (parentClass.isPresent()) {
				Optional<MethodDeclaration> method = parentClass.get()
						.findAll(MethodDeclaration.class)
						.stream()
						.filter(m -> m.getNameAsString().equals(refMethodName))
						.findFirst();
				if (method.isPresent()) {
					return Optional.of(method.get().getType().asString());
				}
				// 递归查找父类的父类
				return resolveFromSuperClassOrInterfaces(cu, parentClass.get(), refMethodName);
			}
		}

		// 查找接口
		if (classDecl.getImplementedTypes().isNonEmpty()) {
			for (var interfaceType : classDecl.getImplementedTypes()) {
				String interfaceName = interfaceType.getNameAsString();
				Optional<ClassOrInterfaceDeclaration> interfaceDecl = findClassDeclaration(interfaceName, cu);
				if (interfaceDecl.isPresent()) {
					Optional<MethodDeclaration> method = interfaceDecl.get()
							.findAll(MethodDeclaration.class)
							.stream()
							.filter(m -> m.getNameAsString().equals(refMethodName))
							.findFirst();
					if (method.isPresent()) {
						return Optional.of(method.get().getType().asString());
					}
				}
			}
		}

		return Optional.empty(); // 未找到方法
	}

	public static String getMethodReturnType(CompilationUnit cu, String parentScope, String methodName) {
		Optional<String> returnType = findMethodReturnType(cu, parentScope, methodName);
		if (returnType.isPresent()) {
			return returnType.get();
		}
		throw new IllegalStateException("无法解析方法 " + methodName + " 的返回类型");
	}
}