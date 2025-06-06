package io.github.jitawangzi.jdepend.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.javaparser.JavaParser;
import com.github.javaparser.JavaToken;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;

/**
 * JavaParser单例工具类
 * 提供预配置的JavaParser实例，避免重复创建和配置
 * 最好不要使用这个，直接使用StaticJavaParser 更简单一些 
 */
@Deprecated
public class JavaParserSingleton {
	private static Logger log = LoggerFactory.getLogger(JavaParserSingleton.class);

	private static volatile JavaParser instance;

	/**
	 * 获取JavaParser实例
	 * @return 配置好的JavaParser实例
	 */
	public static JavaParser getInstance() {
		if (instance == null) {
			synchronized (JavaParserSingleton.class) {
				if (instance == null) {
					ParserConfiguration config = new ParserConfiguration();
					config.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
					instance = new JavaParser(config);
				}
			}
		}
		return instance;
	}

	/**
	 * 解析源代码字符串
	 * @param source 源代码字符串
	 * @return 解析结果
	 */
	public static ParseResult<CompilationUnit> parse(String source) {
		return getInstance().parse(source);
	}

	/**
	 * 解析源代码文件
	 * @param file 源代码文件
	 * @return 解析结果
	 */
	public static ParseResult<CompilationUnit> parse(File file) throws IOException {
		return getInstance().parse(file);
	}

	/**
	 * 解析源代码文件
	 * @param path 源代码文件路径
	 * @return 解析结果
	 */
	public static ParseResult<CompilationUnit> parse(Path path) throws IOException {
		return getInstance().parse(path);
	}

	/**
	 * 解析源代码文件
	 * @param file 源代码文件
	 * @param encoding 文件编码
	 * @return 解析结果
	 */
	public static ParseResult<CompilationUnit> parse(File file, Charset encoding) throws IOException {
		try (FileInputStream in = new FileInputStream(file)) {
			return getInstance().parse(in, encoding);
		}
	}

	public static ParseResult<CompilationUnit> showError(ParseResult<CompilationUnit> parseResult, File file) {

		if (!parseResult.isSuccessful() || !parseResult.getResult().isPresent()) {
			log.error("解析类 {} 失败，可能是语法错误或文件格式不正确", file.getName());
			// 输出具体的解析问题
			if (parseResult.getProblems().size() > 0) {
				log.error("解析问题详情：");
				for (Problem problem : parseResult.getProblems()) {
					String location = "未知位置";

					if (problem.getLocation().isPresent()) {
						JavaToken begin = problem.getLocation().get().getBegin();
						location = "第" + begin.getRange().get().begin.line + "行，第" + begin.getRange().get().begin.column + "列";
					}

					log.error("- 位置：{}，消息：{}，类型：{}", location, problem.getMessage(), problem.getVerboseMessage());
				}
			}
		}
		return parseResult;

	}

	// 私有构造函数，防止实例化
	private JavaParserSingleton() {
	}
}
