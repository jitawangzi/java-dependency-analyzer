package io.github.jitawangzi.jdepend.core.processor;

import java.util.Map;

/**
 * Token计数器，用于估算代码的token数量和计算节省情况
 */
public class TokenCounter {

	/**
	 * 粗略估算文本中的token数量
	 * 注意：这只是一个简化的估算，实际token数量取决于具体的分词算法
	 * 
	 * @param text 文本内容
	 * @return 估算的token数量
	 */
	public static int estimateTokens(String text) {
		if (text == null || text.isEmpty()) {
			return 0;
		}
		// 简单的token估算：平均每4个字符约为1个token
		// 这是一个粗略估计，实际情况会因语言模型而异
		return text.length() / 4;
	}

	/**
	 * 计算原始代码和处理后代码的token差异
	 * 
	 * @param originalContents 原始代码内容
	 * @param processedContents 处理后的代码内容
	 * @return Token统计信息
	 */
	public static TokenStats calculateDifference(Map<String, String> originalContents, Map<String, String> processedContents) {
		int originalTotal = 0;
		int processedTotal = 0;

		for (String className : originalContents.keySet()) {
			String original = originalContents.get(className);
			String processed = processedContents.get(className);
			originalTotal += estimateTokens(original);
			processedTotal += estimateTokens(processed);
		}

		return new TokenStats(originalTotal, processedTotal);
	}

	/**
	 * Token统计信息
	 */
	public static class TokenStats {
		private final int originalTokens;
		private final int processedTokens;

		public TokenStats(int originalTokens, int processedTokens) {
			this.originalTokens = originalTokens;
			this.processedTokens = processedTokens;
		}

		public int getOriginalTokens() {
			return originalTokens;
		}

		public int getProcessedTokens() {
			return processedTokens;
		}

		public int getSavedTokens() {
			return originalTokens - processedTokens;
		}

		public double getSavingPercentage() {
			if (originalTokens == 0)
				return 0;
			return (double) getSavedTokens() / originalTokens * 100;
		}
	}
}