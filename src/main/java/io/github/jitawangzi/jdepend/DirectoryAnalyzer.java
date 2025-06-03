package io.github.jitawangzi.jdepend;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;

import io.github.jitawangzi.jdepend.config.AppConfig;
import io.github.jitawangzi.jdepend.config.RuntimeConfig;
import io.github.jitawangzi.jdepend.core.processor.ContentProcessor;
import io.github.jitawangzi.jdepend.core.processor.TokenCounter;
import io.github.jitawangzi.jdepend.util.ClipboardUtil;
import io.github.jitawangzi.jdepend.util.DirectoryTreeBuilder;
import io.github.jitawangzi.jdepend.util.FileMatcher;

/**
 * 用于将源代码文件转换为提示文本的工具类
 * 以目录为基础，将符合条件的文件合并输出
 * 并生成目录结构和文件内容的Markdown格式
 * 可以选择性地对java源码进行简化，以减少token数量
 */
public class DirectoryAnalyzer {

	// 使用系统换行符
	private static final String LINE_SEPARATOR = System.lineSeparator();
	static {
		// 初始化JavaParser配置
		ParserConfiguration config = new ParserConfiguration();
		config.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
		StaticJavaParser.setConfiguration(config);
	}
	/**
	 * 计数器类，用于跟踪处理的文件数量和令牌数
	 */
	private static class Counter {
		int value = 0;

		void increment() {
			value++;
		}

		void add(int delta) {
			value += delta;
		}

		int getValue() {
			return value;
		}
	}

	/**
	 * 处理文件的主要方法
	 */
	public static void processFiles(String directoryPath) throws IOException {
		AppConfig config = AppConfig.INSTANCE;
		// 获取目录路径
		String dirPath = config.getDirectoryPath();

		Path startPath = Paths.get(dirPath);

		// 创建计数器和构建器
		StringBuilder promptBuilder = new StringBuilder();
		Counter fileCounter = new Counter();
		Counter tokenCounter = new Counter();
		Counter originalTokenCounter = new Counter();
		DirectoryTreeBuilder treeBuilder = new DirectoryTreeBuilder();

		// 存储文件内容
		Map<String, String> originalContents = new LinkedHashMap<>();
		Map<String, String> processedContents = new LinkedHashMap<>();

		// 创建内容处理器
		ContentProcessor contentProcessor = new ContentProcessor(new HashSet<>());

		// 遍历文件系统，同时构建目录树和处理文件内容
		Files.walkFileTree(startPath, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
				// 检查目录是否应该被处理
				if (!shouldProcessDirectory(startPath, dir, config)) {
					return FileVisitResult.SKIP_SUBTREE;
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				// 获取文件的相对路径信息
				String fileName = file.getFileName().toString();
				Path relativePath = startPath.relativize(file.getParent());
				String relativePathStr = normalizePath(relativePath.toString());
				String relativeFilePath = normalizePath(startPath.relativize(file).toString());

				// 检查文件是否应该被处理
				if (!shouldProcessFile(fileName, relativePathStr, config)) {
					return FileVisitResult.CONTINUE;
				}

				// 添加到目录树
				treeBuilder.addFile(startPath, file);

				// 处理文件内容
				fileCounter.increment();
				String content = Files.readString(file, StandardCharsets.UTF_8);
				int originalTokens = TokenCounter.estimateTokens(content);
				originalTokenCounter.add(originalTokens);
				originalContents.put(relativeFilePath, content);

				// 使用ContentProcessor处理Java文件
				String processedContent;
				if (fileName.endsWith(".java")) {
					String className = fileName.substring(0, fileName.lastIndexOf("."));
					processedContent = contentProcessor.process(className, content);
				} else {
					processedContent = content;
				}

				int processedTokens = TokenCounter.estimateTokens(processedContent);
				tokenCounter.add(processedTokens);
				processedContents.put(relativeFilePath, processedContent);

				System.out.printf("Found file: %s (Original Tokens: %d, Processed Tokens: %d)%n", file, originalTokens, processedTokens);

				return FileVisitResult.CONTINUE;
			}
		});

		// 构建提示文本
		promptBuilder.append("# Generated Code Files").append(LINE_SEPARATOR).append(LINE_SEPARATOR);

		// 在文件内容之前添加目录结构
		promptBuilder.append("## Directory Structure").append(LINE_SEPARATOR).append(LINE_SEPARATOR);
		promptBuilder.append("```").append(LINE_SEPARATOR);
		promptBuilder.append("root/").append(LINE_SEPARATOR);
		promptBuilder.append(treeBuilder.getTree());
		promptBuilder.append("```").append(LINE_SEPARATOR).append(LINE_SEPARATOR);

		// 添加文件内容部分的标题
		promptBuilder.append("## File Contents").append(LINE_SEPARATOR).append(LINE_SEPARATOR);

		// 添加处理过的文件内容
		for (Map.Entry<String, String> entry : processedContents.entrySet()) {
			promptBuilder.append("File: ").append(entry.getKey()).append(LINE_SEPARATOR);
			promptBuilder.append("Content:").append(LINE_SEPARATOR).append(entry.getValue()).append(LINE_SEPARATOR);
			promptBuilder.append("---").append(LINE_SEPARATOR);
		}

		if (fileCounter.getValue() > 0) {
			// 计算token统计
			TokenCounter.TokenStats tokenStats = TokenCounter.calculateDifference(originalContents, processedContents);
			String prompt = promptBuilder.toString();
			if (prompt.length() < config.getContentSizeThreshold()) {
				// 复制到剪贴板
				ClipboardUtil.copyToClipboard(prompt);
			}

			// 添加总结部分
			promptBuilder.append(LINE_SEPARATOR + "## Summary").append(LINE_SEPARATOR).append(LINE_SEPARATOR);
			promptBuilder.append("- Total files processed: ").append(fileCounter.getValue()).append(LINE_SEPARATOR);
			promptBuilder.append("- Original tokens: ").append(tokenStats.getOriginalTokens()).append(LINE_SEPARATOR);
			promptBuilder.append("- Processed tokens: ").append(tokenStats.getProcessedTokens()).append(LINE_SEPARATOR);
			promptBuilder.append("- Tokens saved: ").append(tokenStats.getSavedTokens()).append(LINE_SEPARATOR);

			System.out.println(LINE_SEPARATOR + "Generated Prompt:");
			System.out.println(prompt);

			System.out.printf(LINE_SEPARATOR + "Total files processed: %d%n", fileCounter.getValue());
			System.out.printf("Original tokens: %d%n", tokenStats.getOriginalTokens());
			System.out.printf("Processed tokens: %d%n", tokenStats.getProcessedTokens());
			System.out.printf("Tokens saved: %d%n", tokenStats.getSavedTokens());

			Files.writeString(Paths.get(config.getOutputFile()), prompt);
			System.out.println("Prompt has been saved to 'generated_prompt.md'");
		} else {
			System.out.println("No matching files found.");
		}
	}

	/**
	 * 规范化路径，将反斜杠替换为正斜杠
	 */
	private static String normalizePath(String path) {
		return path.replace('\\', '/');
	}

	/**
	 * 检查目录是否应该被处理
	 */
	private static boolean shouldProcessDirectory(Path startPath, Path dir, AppConfig config) {
		// 计算当前目录的深度
		int currentDepth = startPath.relativize(dir).getNameCount();

		// 检查是否超过最大深度限制
		if (config.getMaxDepth() >= 0 && currentDepth > config.getMaxDepth()) {
			return false;
		}

		// 如果指定了包含文件，允许遍历所有目录以找到目标文件
		if (!config.getIncludeFiles().isEmpty()) {
			return true;
		}

		Path relativePath = startPath.relativize(dir);
		String relativePathStr = normalizePath(relativePath.toString());
		if (relativePathStr.isEmpty()) {
			return true;
		}

		// 检查排除目录
		if (config.getExcludeFolders()
				.stream()
				.anyMatch(exclude -> relativePathStr.equals(exclude) || relativePathStr.startsWith(exclude + "/"))) {
			return false;
		}

		// 如果指定了包含目录
		if (!config.getIncludeFolders().isEmpty()) {
			boolean isIncluded = false;
			boolean isParentOfIncluded = false;

			for (String include : config.getIncludeFolders()) {
				String normalizedInclude = normalizePath(include);
				if (relativePathStr.equals(normalizedInclude) || relativePathStr.startsWith(normalizedInclude + "/")) {
					isIncluded = true;
					break;
				}
				// 检查当前目录是否是包含目录的父路径
				if (normalizedInclude.startsWith(relativePathStr + "/")) {
					isParentOfIncluded = true;
					break;
				}
			}

			// 如果是父路径，继续遍历但不处理文件
			if (isParentOfIncluded) {
				return true;
			}

			// 如果不是包含目录或其子目录，也不是父路径，则跳过
			return isIncluded;
		}

		return true;
	}

	/**
	 * 检查文件是否应该被处理
	 */
	private static boolean shouldProcessFile(String fileName, String relativePathStr, AppConfig config) {
		// 检查文件类型
		if (!isFileAllowed(fileName)) {
			return false;
		}

		// 检查排除文件
		if (new FileMatcher(config.getExcludeFiles()).matches(fileName)) {
			return false;
		}

		// 处理包含文件
		if (!config.getIncludeFiles().isEmpty()) {
			return new FileMatcher(config.getIncludeFiles()).matches(fileName);
		}

		// 处理包含目录中的文件
		if (!config.getIncludeFolders().isEmpty()) {
			return config.getIncludeFolders().stream().anyMatch(include -> {
				String normalizedInclude = normalizePath(include);
				return relativePathStr.equals(normalizedInclude) || relativePathStr.startsWith(normalizedInclude + "/");
			});
		}

		return true;
	}

	/**
	 * 检查文件扩展名是否在允许列表中
	 */
	private static boolean isFileAllowed(String fileName) {
		String extension = getFileExtension(fileName);
		Set<String> allowedFileExtensions = AppConfig.INSTANCE.getAllowedFileExtensions();
		return allowedFileExtensions.isEmpty() || allowedFileExtensions.contains(extension.toLowerCase());
	}

	/**
	 * 获取文件扩展名
	 */
	private static String getFileExtension(String fileName) {
		int lastDotIndex = fileName.lastIndexOf('.');
		if (lastDotIndex > 0) {
			return fileName.substring(lastDotIndex + 1);
		}
		return "";
	}


	public static void main(String[] args) {
		try {
			RuntimeConfig.isDirectoryMode = true; // 设置为目录模式

			String directory = AppConfig.INSTANCE.getDirectoryPath();

			if (directory == null || directory.isEmpty()) {
				// 如果没有指定目录，使用当前目录
				directory = System.getProperty("user.dir");
				System.out.println("No directory specified, using current directory: " + directory);
			}
			processFiles(directory);
		} catch (Exception e) {
			System.err.println("Error processing files: " + e.getMessage());
			e.printStackTrace();
		}
	}
}