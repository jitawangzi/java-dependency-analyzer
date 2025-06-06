package io.github.jitawangzi.jdepend;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.jitawangzi.jdepend.config.AppConfig;
import io.github.jitawangzi.jdepend.core.analyzer.DependencyCollector;
import io.github.jitawangzi.jdepend.core.analyzer.MethodDependencyAnalyzer;
import io.github.jitawangzi.jdepend.core.generator.MarkdownGenerator;
import io.github.jitawangzi.jdepend.core.model.ClassDependency;
import io.github.jitawangzi.jdepend.core.processor.ContentProcessor;
import io.github.jitawangzi.jdepend.core.processor.TokenCounter;
import io.github.jitawangzi.jdepend.util.ClipboardUtil;
import io.github.jitawangzi.jdepend.util.FileLocator;
import io.github.jitawangzi.jdepend.util.JavaParserInit;

/**
 * 用于将Java源代码文件转换为提示文本的工具类
 * 以指定的java类为起点，分析其依赖关系并生成Markdown格式的输出
 * 可以选择性地对java源码进行简化，以减少token数量
 */
public class ClassAnalyzer {
	private static Logger log = LoggerFactory.getLogger(ClassAnalyzer.class);
	/**
	 * 主方法
	 * 
	 * @param args 命令行参数
	 * @throws Exception 如果分析过程中发生错误
	 */
	public static void main(String[] args) throws Exception {
		JavaParserInit.init();
		// 加载配置
		AppConfig config = AppConfig.INSTANCE;
		// 初始化文件定位器
		FileLocator locator = new FileLocator();
		locator.addSourceDirectories(config.getSourceDirectories());

		// 1. 常规分析
		DependencyCollector collector = new DependencyCollector();
		List<ClassDependency> dependencies = collector.collect();

		// 2. 方法级依赖分析
		log.info("正在进行方法级依赖分析...");
		MethodDependencyAnalyzer methodAnalyzer = new MethodDependencyAnalyzer();
		Set<String> actualDependencies = methodAnalyzer.analyzeAllDependencies(config.getMainClass());
		Set<String> reachableMethods = methodAnalyzer.getReachableMethods();
		// 3. 获取实际依赖的类
		log.info("方法级依赖分析完成，发现 {} 个实际依赖类（传统分析发现 {} 个类）", actualDependencies.size(), dependencies.size());
		log.debug("可达方法总数: {}", reachableMethods.size());

		// 4. 基于实际依赖过滤依赖列表
		List<ClassDependency> filteredDependencies = dependencies.stream()
				.filter(dep -> actualDependencies.contains(dep.getClassName()))
				.collect(Collectors.toList());


		// 处理代码内容
		ContentProcessor processor = new ContentProcessor(reachableMethods);
		// 存储原始内容和处理后的内容
		Map<String, String> originalContents = new LinkedHashMap<>();
		Map<String, String> processedContents = new LinkedHashMap<>();

		for (ClassDependency dep : filteredDependencies) {

			if ((config.getMaxDepth() != -1 && dep.getDepth() > config.getMaxDepth())) {
				continue; // 如果超过最大深度，则跳过
			}

			Path file = locator.locate(dep.getClassName());
			if (file == null) {
				log.warn("无法找到类文件: {}", dep.getClassName());
				continue; // 如果找不到文件，则跳过，比如内部类
			}
			String original = Files.readString(file);
			originalContents.put(dep.getClassName(), original);
			processedContents.put(dep.getClassName(), processor.process(original));
		}

		// 计算token统计
		TokenCounter.TokenStats tokenStats = TokenCounter.calculateDifference(originalContents, processedContents);

		// 生成markdown内容
		MarkdownGenerator generator = new MarkdownGenerator();
		String output = generator.generate(filteredDependencies, processedContents, tokenStats, processor.getOmittedAccessors(),
				processor.getRemovedUnreferencedMethods());

		// 写入文件
		Path outputFile = Path.of(config.getOutputFile());
		Files.writeString(outputFile, output);
		log.info("结果已写入: {}", outputFile.toAbsolutePath());

		// 根据内容大小决定是否在控制台输出全部内容
		if (output.length() < config.getContentSizeThreshold()) {
			// 内容不多，输出全部并复制到剪切板
			System.out.println("\n=== 输出内容 ===\n");
			System.out.println(output);
			ClipboardUtil.copyToClipboard(output);
		} else {
			// 内容太多，只输出摘要
			String summary = generator.generateSummary(filteredDependencies, tokenStats);
			System.out.println("\n=== 输出摘要 ===\n");
			System.out.println(summary);
			System.out.println("内容过多，未复制到剪切板。完整内容请查看输出文件。");
		}
	}
}