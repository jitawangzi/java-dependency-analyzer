package io.github.jitawangzi.jdepend.core.generator;

import java.util.List;
import java.util.Map;

import com.github.javaparser.ast.body.MethodDeclaration;

import io.github.jitawangzi.jdepend.config.AppConfig;
import io.github.jitawangzi.jdepend.config.ImportConfig;
import io.github.jitawangzi.jdepend.core.model.ClassDependency;
import io.github.jitawangzi.jdepend.core.model.MethodDependency;
import io.github.jitawangzi.jdepend.core.model.MethodReference;
import io.github.jitawangzi.jdepend.core.processor.TokenCounter;
import io.github.jitawangzi.jdepend.core.processor.TokenCounter.TokenStats;

/**
 * Markdown生成器，用于生成分析结果的Markdown文档
 */
public class MarkdownGenerator {
	
    /**
     * 构造函数
     * 
     * @param config 配置对象
     */
	public MarkdownGenerator() {
    }
	/**
	 * 生成完整的Markdown文档
	 * 
	 * @param dependencies 依赖列表
	 * @param classContents 类内容映射
	 * @param tokenStats 令牌统计信息
	 * @return 生成的Markdown文档
	 */
	public String generate(List<ClassDependency> dependencies, Map<String, String> classContents, TokenCounter.TokenStats tokenStats,
			Map<String, List<MethodDeclaration>> omittedAccessors, Map<String, List<MethodDeclaration>> removedUnreferencedMethods) {
		StringBuilder sb = new StringBuilder();
		sb.append("# Code Context Analysis\n\n");

		// 添加Token统计信息
		appendTokenStats(sb, tokenStats);

		// 添加导入提示信息
		appendImportInfo(sb);

		appendGetterAndSetterInfo(sb);

		// 添加依赖树
		appendDependencyTree(sb, dependencies);

		// 添加代码内容
		appendCodeContents(sb, classContents);

		// 添加被省略的Bean方法
		if (AppConfig.INSTANCE.isShowOmittedAccessors() && AppConfig.INSTANCE.isOmitBeanMethods() && !omittedAccessors.isEmpty()) {
			appendOmittedAccessors(sb, omittedAccessors);
		}
		// 添加被移除的未引用方法
		if (AppConfig.INSTANCE.isKeepOnlyReferencedMethods() && AppConfig.INSTANCE.isShowRemovedMethods()
				&& !removedUnreferencedMethods.isEmpty()) {
			appendRemovedUnreferencedMethods(sb, removedUnreferencedMethods);
		}

		return sb.toString();
	}

	/**
	 * 添加被移除的未引用方法信息
	 */
	private void appendRemovedUnreferencedMethods(StringBuilder sb, Map<String, List<MethodDeclaration>> removedMethods) {
		sb.append("## Removed Unreferenced Methods\n\n");
		sb.append("以下方法未被主类或其依赖方法引用，已被移除以减少代码量：\n\n");

		for (Map.Entry<String, List<MethodDeclaration>> entry : removedMethods.entrySet()) {
			String className = entry.getKey();
			List<MethodDeclaration> methods = entry.getValue();

			if (methods.isEmpty()) {
				continue;
			}

			sb.append("### ").append(className).append("\n\n");
			sb.append("```java\n");

			for (MethodDeclaration method : methods) {
				// 只输出方法签名，不包含方法体
				sb.append(method.getDeclarationAsString(false, false, false)).append(";\n");
			}

			sb.append("```\n\n");
		}
	}

	/**
	 * 添加令牌统计信息
	 * 
	 * @param sb StringBuilder对象
	 * @param stats 令牌统计信息
	 */
	private void appendTokenStats(StringBuilder sb, TokenCounter.TokenStats stats) {
		sb.append("## Token Statistics\n");
		sb.append("- Original tokens: ").append(stats.getOriginalTokens()).append("\n");
		sb.append("- Processed tokens: ").append(stats.getProcessedTokens()).append("\n");
		sb.append("- Tokens saved: ")
				.append(stats.getSavedTokens())
				.append(" (")
				.append(String.format("%.2f", stats.getSavingPercentage()))
				.append("%)\n\n");
	}

	/**
	 * 添加导入信息
	 * 
	 * @param sb StringBuilder对象
	 */
	private void appendImportInfo(StringBuilder sb) {
		if (ImportConfig.INSTANCE.isSkipEnabled()) {
			sb.append("## Import Information\n");
			sb.append("为减少代码体积，分析器已忽略了某些导入声明。通常这些是jdk常见类，或者项目内部类, " + "这不会影响代码分析的完整性，但在查看源代码时可能会发现某些导入语句被省略。\n\n");
		}
	}

	/**
	 * 添加getter/setter信息
	 * 
	 * @param sb StringBuilder对象
	 */
	private void appendGetterAndSetterInfo(StringBuilder sb) {
		if (AppConfig.INSTANCE.isOmitBeanMethods()) {
			sb.append("## Getter and Setter Information\n");
			sb.append("为减少代码体积，分析器可能忽略了一些标准的java bean getter  setter方法。\n\n");
		}
	}

	/**
	 * 添加依赖树
	 * 
	 * @param sb StringBuilder对象
	 * @param deps 依赖列表
	 */
	private void appendDependencyTree(StringBuilder sb, List<ClassDependency> deps) {
		sb.append("## Dependency Tree\n```\n");
		deps.forEach(d -> sb.append(indent(d.getDepth())).append(d.getClassName()).append("\n"));
		sb.append("```\n\n");
	}

	/**
	 * 生成缩进字符串
	 * 
	 * @param depth 深度
	 * @return 缩进字符串
	 */
	private String indent(int depth) {
		return "  ".repeat(Math.max(0, depth));
	}

	/**
	 * 添加代码内容
	 * 
	 * @param sb StringBuilder对象
	 * @param contents 代码内容映射
	 */
	private void appendCodeContents(StringBuilder sb, Map<String, String> contents) {
		sb.append("## Code Contents\n");
		contents.forEach((k, v) -> sb.append("### ").append(k).append("\n```java\n").append(v).append("\n```\n\n"));
	}

	/**
	 * 生成内容摘要
	 * 
	 * @param dependencies 依赖列表
	 * @param tokenStats 令牌统计信息
	 * @return 生成的摘要
	 */
	public String generateSummary(List<ClassDependency> dependencies, TokenCounter.TokenStats tokenStats) {
		StringBuilder sb = new StringBuilder();
		sb.append("# Code Context Analysis Summary\n\n");

		// 添加Token统计信息
		appendTokenStats(sb, tokenStats);

		// 添加导入提示信息
		appendImportInfo(sb);

		// 添加类数量信息
		sb.append("## Classes\n");
		sb.append("- Total classes: ").append(dependencies.size()).append("\n\n");

		// 添加依赖树的简短预览（只显示前几个类）
		sb.append("## Dependency Tree Preview\n```\n");
		int previewCount = Math.min(dependencies.size(), 5);
		dependencies.stream().limit(previewCount).forEach(d -> sb.append(indent(d.getDepth())).append(d.getClassName()).append("\n"));
		if (dependencies.size() > previewCount) {
			sb.append("... and ").append(dependencies.size() - previewCount).append(" more classes\n");
		}
		sb.append("```\n\n");

		sb.append("Full content has been written to output.md\n");
		return sb.toString();
	}

	/**
	 * 展示方法级依赖关系
	 * 
	 * @param sb StringBuilder对象
	 * @param methodDependencies 方法依赖映射
	 */
	public void appendMethodDependencies(StringBuilder sb, Map<String, List<MethodDependency>> methodDependencies) {
		sb.append("## Method Dependencies\n\n");
		sb.append("This section shows the actual method-level dependencies between classes.\n\n");

		for (Map.Entry<String, List<MethodDependency>> entry : methodDependencies.entrySet()) {
			String className = entry.getKey();
			List<MethodDependency> methods = entry.getValue();

			sb.append("### ").append(className).append("\n\n");

			for (MethodDependency method : methods) {
				sb.append("- **").append(method.getMethodSignature()).append("**\n");

				if (!method.getReferencedTypes().isEmpty()) {
					sb.append("  - Referenced types: ");
					sb.append(String.join(", ", method.getReferencedTypes()));
					sb.append("\n");
				}

				if (!method.getCalledMethods().isEmpty()) {
					sb.append("  - Calls methods:\n");
					for (MethodReference ref : method.getCalledMethods()) {
						sb.append("    - ").append(ref.getClassName()).append("#").append(ref.getMethodName()).append("\n");
					}
				}

				sb.append("\n");
			}
		}
	}
	
    
    /**
     * 添加被省略的访问器方法信息
     * 
     * @param sb StringBuilder对象
     * @param omittedAccessors 被省略的访问器方法
     */
    private void appendOmittedAccessors(StringBuilder sb, Map<String, List<MethodDeclaration>> omittedAccessors) {
        sb.append("## Omitted Bean Accessors\n\n");
        sb.append("以下JavaBean访问器方法（getter/setter）已被省略以减少代码量：\n\n");
        
        for (Map.Entry<String, List<MethodDeclaration>> entry : omittedAccessors.entrySet()) {
            String className = entry.getKey();
            List<MethodDeclaration> methods = entry.getValue();
            
            if (methods.isEmpty()) {
                continue;
            }
            
            sb.append("### ").append(className).append("\n\n");
            sb.append("```java\n");
            
            for (MethodDeclaration method : methods) {
                // 只输出方法签名，不包含方法体
                sb.append(method.getDeclarationAsString(false, false, false)).append(";\n");
            }
            
            sb.append("```\n\n");
        }
    }
}