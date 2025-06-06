package io.github.jitawangzi.jdepend.util;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import io.github.jitawangzi.jdepend.config.AppConfig;

/**
 * 文件定位器，用于定位Java类文件
 */
public class FileLocator {
	private final PathMatcher javaMatcher;

	// 常见的源码目录
	private static final List<String> COMMON_SOURCE_DIRS = new ArrayList<>();

	/**
	 * 构造函数
	 * 
	 * @param config 配置对象
	 */
	public FileLocator() {
		this.javaMatcher = FileSystems.getDefault().getPathMatcher("glob:**.java");
	}

	/**
	 * 定位类文件
	 * 
	 * @param className 类名
	 * @return 类文件路径，如果找不到则返回null
	 * @throws IOException 如果定位过程中发生IO错误
	 */
	public Path locate(String className) throws IOException {
		String relativePath = className.replace('.', '/') + ".java";
		List<Path> matches = new ArrayList<>();

		Files.walkFileTree(Paths.get(AppConfig.INSTANCE.getProjectRootPath()), new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				if (javaMatcher.matches(file) && file.endsWith(relativePath)) {
					matches.add(file);
					return FileVisitResult.TERMINATE;
				}
				return FileVisitResult.CONTINUE;
			}
		});

		return matches.isEmpty() ? null : matches.get(0);
	}
	/**
	 * 添加额外的源码目录支持
	 * 
	 * @param config 配置对象
	 */
	public void addSourceDirectories(List<String> sourceDirs) {
		if (sourceDirs != null && !sourceDirs.isEmpty()) {
			COMMON_SOURCE_DIRS.addAll(sourceDirs);
		}
	}
}