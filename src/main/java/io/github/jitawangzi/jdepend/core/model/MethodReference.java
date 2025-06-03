package io.github.jitawangzi.jdepend.core.model;

import java.util.Objects;

/**
 * 表示方法调用引用
 */
public class MethodReference {
	private final String className;
	private final String methodName;
	private final String signature;

	/**
	 * 构造函数
	 * 
	 * @param className 类名
	 * @param methodName 方法名
	 * @param signature 方法签名
	 */
	public MethodReference(String className, String methodName, String signature) {
		this.className = className;
		this.methodName = methodName;
		this.signature = signature;
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
	 * 获取方法名
	 * 
	 * @return 方法名
	 */
	public String getMethodName() {
		return methodName;
	}

	/**
	 * 获取方法签名
	 * 
	 * @return 方法签名
	 */
	public String getSignature() {
		return signature;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		MethodReference that = (MethodReference) o;
		return Objects.equals(className, that.className) && Objects.equals(methodName, that.methodName)
				&& Objects.equals(signature, that.signature);
	}

	@Override
	public int hashCode() {
		return Objects.hash(className, methodName, signature);
	}

	@Override
	public String toString() {
		return className + "#" + methodName + signature;
	}
}