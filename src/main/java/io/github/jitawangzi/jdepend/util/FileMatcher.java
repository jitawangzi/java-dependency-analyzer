package io.github.jitawangzi.jdepend.util;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**    
 * 文件名匹配工具类，支持通配符*和?
 * 2024年12月16日 19:16:49
 * @author SYQ
 */
public class FileMatcher {
	private final Set<String> names;
	private final Set<Pattern> patterns;

	public FileMatcher() {
		this.names = new HashSet<>();
		this.patterns = new HashSet<>();
	}

	public FileMatcher(List<String> patterns) {
		this();
		for (String pattern : patterns) {
			addPattern(pattern);
		}
	}

	/**
	 * 添加名称或通配符模式
	 * @param pattern 名称或通配符模式
	 * @param caseSensitive 是否区分大小写
	 */
	public void addPattern(String pattern, boolean caseSensitive) {
		if (pattern == null || pattern.trim().isEmpty()) {
			return;
		}
		if (containsWildcard(pattern)) {
			// 将通配符转换为正则表达式
			String regex = wildcardToRegex(pattern);
			if (caseSensitive) {
				patterns.add(Pattern.compile(regex)); // 默认区分大小写
			} else {
				patterns.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
			}
		} else {
			names.add(pattern);
		}


	}

	/**
	 * 添加名称或通配符模式
	 * @param pattern 名称或通配符模式
	 */
	public void addPattern(String pattern) {
		addPattern(pattern, true);
	}

	/**
	 * 检查字符串是否包含通配符
	 */
	private boolean containsWildcard(String str) {
		return str.contains("*") || str.contains("?");
	}

	/**
	 * 将通配符转换为正则表达式
	 * * 匹配0个或多个字符
	 * ? 匹配1个字符
	 */
	private String wildcardToRegex(String wildcard) {
		StringBuilder regex = new StringBuilder();
		regex.append('^');
		for (char c : wildcard.toCharArray()) {
			switch (c) {
			case '*':
				regex.append(".*");
				break;
			case '?':
				regex.append('.');
				break;
			default:
				// 转义特殊字符
				if ("[](){}+^$.|\\".indexOf(c) != -1) {
					regex.append('\\');
				}
				regex.append(c);
			}
		}
		regex.append('$');
		return regex.toString();
	}

	/**
	 * 判断名称是否匹配
	 * @param name 要检查的名称
	 * @return 是否匹配
	 */
	public boolean matches(String name) {
		if (name == null || name.trim().isEmpty()) {
			return false;
		}

		// 首先检查精确匹配
		if (names.contains(name)) {
			return true;
		}

		// 然后检查正则匹配
		for (Pattern pattern : patterns) {
			if (pattern.matcher(name).matches()) {
				return true;
			}
		}

		return false;
	}

	/** 
	 * 
	 * @param pattern
	 * @return
	 */
	private boolean isDirectoryPattern(String pattern) {
		// 显式指定
		if (pattern.startsWith("dir:")) {
			return true;
		}
		if (pattern.startsWith("file:")) {
			return false;
		}

		// 常规判断
		if (pattern.endsWith("/") || pattern.endsWith("\\")) {
			return true;
		}
		if (pattern.contains("/**")) {
			return true;
		}

		// 根据项目约定判断
		if (!pattern.contains(".") && !pattern.contains("*")) {
			// 可以添加额外的判断逻辑
			// 例如：检查是否符合项目的目录命名规范
			return true;
		}

		return false;
	}

	public boolean isEmpty() {
		return names.isEmpty() && patterns.isEmpty();
	}

	// 使用示例
	public static void main(String[] args) {
		FileMatcher matcher = new FileMatcher();

		// 添加模式
		matcher.addPattern("protocol*");
		matcher.addPattern("test???");
		matcher.addPattern("exact");
//		matcher.addPattern("protocol/target");

		// 测试匹配
		System.out.println(matcher.matches("protocoltest")); // true
		System.out.println(matcher.matches("protocol")); // true
		System.out.println(matcher.matches("protocol123")); // true
		System.out.println(matcher.matches("test123")); // true
		System.out.println(matcher.matches("test12")); // false
		System.out.println(matcher.matches("exact")); // true
		System.out.println(matcher.matches("other")); // false
	}
}