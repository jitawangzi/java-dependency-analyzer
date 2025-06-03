package io.github.jitawangzi.jdepend.core.model;

import java.util.HashSet;
import java.util.Set;

/**
 * 表示方法依赖关系
 */
public class MethodDependency {
	private final String className;
	private final String methodSignature;
	private final Set<String> referencedTypes = new HashSet<>();
	private final Set<MethodReference> calledMethods = new HashSet<>();

	/**
	 * 构造函数
	 * 
	 * @param className 类名
	 * @param methodSignature 方法签名
	 */
	public MethodDependency(String className, String methodSignature) {
		this.className = className;
		this.methodSignature = methodSignature;
	}

	/**
	 * 添加引用的类型
	 * 
	 * @param typeName 类型名
	 */
	public void addReferencedType(String typeName) {
		if (typeName != null && !typeName.isEmpty()) {
			referencedTypes.add(typeName);
		}
	}

	/**
	 * 添加调用的方法
	 * 
	 * @param methodRef 方法引用
	 */
	public void addCalledMethod(MethodReference methodRef) {
		calledMethods.add(methodRef);
	}

	/**
	 * 获取类名
	 * 
	 * @return 类名
	 */
	public String getClassName() {
		return className;
	}

	/**
	 * 获取方法签名
	 * 
	 * @return 方法签名
	 */
	public String getMethodSignature() {
		return methodSignature;
	}

	/**
	 * 获取引用的类型
	 * 
	 * @return 引用的类型集合
	 */
	public Set<String> getReferencedTypes() {
		return referencedTypes;
	}

	/**
	 * 获取调用的方法
	 * 
	 * @return 调用的方法集合
	 */
	public Set<MethodReference> getCalledMethods() {
		return calledMethods;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		MethodDependency that = (MethodDependency) o;
		return className.equals(that.className) && methodSignature.equals(that.methodSignature);
	}

	@Override
	public int hashCode() {
		return 31 * className.hashCode() + methodSignature.hashCode();
	}

	@Override
	public String toString() {
		return className + "#" + methodSignature;
	}
}