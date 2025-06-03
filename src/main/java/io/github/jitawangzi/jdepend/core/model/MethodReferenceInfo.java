package io.github.jitawangzi.jdepend.core.model;

import java.util.HashSet;
import java.util.Set;

/**
 * 存储方法引用信息
 */
public class MethodReferenceInfo {
	private final String className;
	private final String methodName;
	private final Set<String> callerMethods = new HashSet<>();

	public MethodReferenceInfo(String className, String methodName) {
		this.className = className;
		this.methodName = methodName;
	}

	public void addCaller(String callerMethod) {
		callerMethods.add(callerMethod);
	}

	public String getClassName() {
		return className;
	}

	public String getMethodName() {
		return methodName;
	}

	public Set<String> getCallerMethods() {
		return callerMethods;
	}

	public String getFullName() {
		return className + "." + methodName;
	}
}