package io.github.jitawangzi.jdepend.util;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class DirectoryTreeBuilder {

	// 使用系统换行符
	private static final String LINE_SEPARATOR = System.lineSeparator();
	private final StringBuilder treeBuilder = new StringBuilder();
	private final Set<String> processedPaths = new HashSet<>();

	public void addFile(Path basePath, Path file) {
		Path relativePath = basePath.relativize(file);
		String[] parts = relativePath.toString().replace('\\', '/').split("/");
		StringBuilder currentPath = new StringBuilder();

		for (int i = 0; i < parts.length; i++) {
			String part = parts[i];
			if (i > 0) {
				currentPath.append("/");
			}
			currentPath.append(part);
			String pathStr = currentPath.toString();

			if (!processedPaths.contains(pathStr)) {
				processedPaths.add(pathStr);
				// Add indentation
				treeBuilder.append("│   ".repeat(i));
				// Add directory or file marker
				if (i < parts.length - 1) {
					treeBuilder.append("├── ");
				} else {
					treeBuilder.append("└── ");
				}
				// Add name and optional comment for files
				treeBuilder.append(part);
				if (i == parts.length - 1) {
					String comment = getFileComment(part);
					if (comment != null) {
						treeBuilder.append("    # ").append(comment);
					}
				}
				treeBuilder.append(LINE_SEPARATOR);
			}
		}
	}

	private String getFileComment(String fileName) {
		// Add descriptive comments for different file types
		if (fileName.endsWith(".java")) {
			return "Java source file";
		} else if (fileName.endsWith(".proto")) {
			return "Protocol buffer definition";
		} else if (fileName.endsWith(".xml")) {
			return "Configuration file";
		} else if (fileName.endsWith(".properties")) {
			return "Properties configuration";
		}
		return null;
	}

	public String getTree() {
		return treeBuilder.toString();
	}

}
