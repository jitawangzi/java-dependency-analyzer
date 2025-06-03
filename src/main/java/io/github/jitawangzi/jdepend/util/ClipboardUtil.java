package io.github.jitawangzi.jdepend.util;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;

/**
 * 剪切板工具类
 */
public class ClipboardUtil {

	/**
	 * 将文本复制到系统剪切板
	 * 
	 * @param text 要复制的文本
	 */
	public static void copyToClipboard(String text) {
		try {
			StringSelection selection = new StringSelection(text);
			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			clipboard.setContents(selection, null);
			System.out.println("内容已复制到剪切板");
		} catch (Exception e) {
			System.err.println("复制到剪切板失败: " + e.getMessage());
		}
	}
}