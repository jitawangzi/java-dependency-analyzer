package io.github.jitawangzi.jdepend.core.model;

/**
 * 类依赖模型，表示一个类的依赖关系
 */
public class ClassDependency {
	private final String className;
	private final int depth;

	/**
	 * 构造函数
	 * 
	 * @param className 类名
	 * @param depth 依赖深度
	 */
	public ClassDependency(String className, int depth) {
		this.className = className;
		this.depth = depth;
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
	 * 获取依赖深度
	 * 
	 * @return 依赖深度
	 */
	public int getDepth() {
		return depth;
	}
}