package io.github.jitawangzi.jdepend.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;

/**
 * Java源码读取工具类，支持读取普通类和内部类的源代码
 */
public class JavaSourceUtil {

	/**
	 * 读取类的源代码，包括处理内部类
	 * 
	 * @param className 类的全限定名
	 * @param locator 文件定位器
	 * @return 类的源代码
	 * @throws IOException 如果读取文件失败
	 * @throws IllegalStateException 如果找不到类文件
	 */
	public static String readClassSource(String className, FileLocator locator) throws IOException {
		// 首先尝试直接作为普通类查找
		Path file = locator.locate(className);

		if (file != null) {
			// 找到了普通类文件，直接读取并返回
			return Files.readString(file);
		}

		// 如果找不到文件，检查是否是内部类
		int lastDotIndex = className.lastIndexOf(".");
		if (lastDotIndex != -1) {
			String potentialOuterClass = className.substring(0, lastDotIndex);
			String innerClassName = className.substring(lastDotIndex + 1);

			// 尝试定位外部类文件
			Path outerClassFile = locator.locate(potentialOuterClass);

			if (outerClassFile != null) {
				// 找到了外部类文件，解析它并查找内部类
				String outerClassSource = Files.readString(outerClassFile);

				// 使用JavaParser解析外部类源码
				CompilationUnit cu = StaticJavaParser.parse(outerClassSource);

				// 获取外部类的简单名称（不包含包名）
				String outerSimpleName = potentialOuterClass.substring(potentialOuterClass.lastIndexOf('.') + 1);

				// 在编译单元中查找外部类定义
				Optional<ClassOrInterfaceDeclaration> outerClassOpt = cu.findFirst(ClassOrInterfaceDeclaration.class,
						c -> c.getNameAsString().equals(outerSimpleName));

				if (outerClassOpt.isPresent()) {
					// 在外部类中查找内部类定义
					Optional<ClassOrInterfaceDeclaration> innerClassOpt = outerClassOpt.get()
							.findFirst(ClassOrInterfaceDeclaration.class, c -> c.getNameAsString().equals(innerClassName));

					if (innerClassOpt.isPresent()) {
						// 找到了内部类，返回其源码
						return innerClassOpt.get().toString();
					}
				}
			}
		}

		// 如果所有尝试都失败，抛出异常
		throw new IllegalStateException("无法找到类文件: " + className + "，请检查配置或源码目录是否正确");
	}

	/**
	 * 检查类名是否表示内部类
	 * 
	 * @param className 类名
	 * @return 如果是内部类返回true，否则返回false
	 */
	public static boolean isInnerClass(String className, FileLocator locator) throws IOException {
		// 直接尝试定位类文件
		if (locator.locate(className) != null) {
			return false; // 找到了直接对应的文件，不是内部类
		}

		// 检查是否可能是内部类（最后一个点号后有类名）
		int lastDotIndex = className.lastIndexOf(".");
		if (lastDotIndex != -1) {
			String potentialOuterClass = className.substring(0, lastDotIndex);
			return locator.locate(potentialOuterClass) != null;
		}

		return false;
	}

	/**
	 * 获取内部类的外部类名
	 * 
	 * @param innerClassName 内部类的全限定名
	 * @return 外部类的全限定名，如果不是内部类则返回null
	 */
	public static String getOuterClassName(String innerClassName, FileLocator locator) throws IOException {
		if (!isInnerClass(innerClassName, locator)) {
			return null;
		}

		int lastDotIndex = innerClassName.lastIndexOf(".");
		if (lastDotIndex != -1) {
			return innerClassName.substring(0, lastDotIndex);
		}

		return null;
	}

	/**
	 * 从外部类中提取并解析内部类
	 * 
	 * @param className 完整类名（包含内部类）
	 * @param locator 文件定位器
	 * @return 解析后的内部类内容
	 * @throws Exception 如果解析过程中发生错误
	 */
	public static String extractAndProcessInnerClass(String className, FileLocator locator) throws Exception {
		// 检查是否是内部类（通过点号判断）
		int lastDotIndex = className.lastIndexOf(".");
		if (lastDotIndex == -1) {
			throw new IllegalArgumentException("无效的类名: " + className);
		}

		// 获取可能的外部类名和内部类名
		String potentialOuterClass = className.substring(0, lastDotIndex);
		String innerClassName = className.substring(lastDotIndex + 1);

		// 定位外部类文件
		Path outerClassFile = locator.locate(potentialOuterClass);
		if (outerClassFile == null) {
			throw new IllegalStateException("找不到外部类文件: " + potentialOuterClass);
		}

		// 读取外部类源码
		String outerClassSource = Files.readString(outerClassFile);

		// 解析完整的外部类文件
		CompilationUnit cu = StaticJavaParser.parse(outerClassFile.toFile());

		// 获取外部类的简单名称
		String outerSimpleName = potentialOuterClass.substring(potentialOuterClass.lastIndexOf('.') + 1);

		// 在编译单元中查找外部类
		Optional<ClassOrInterfaceDeclaration> outerClassOpt = cu.findFirst(ClassOrInterfaceDeclaration.class,
				c -> c.getNameAsString().equals(outerSimpleName));

		if (!outerClassOpt.isPresent()) {
			throw new IllegalStateException("在源文件中找不到外部类: " + outerSimpleName);
		}

		// 在外部类中查找内部类
		Optional<ClassOrInterfaceDeclaration> innerClassOpt = outerClassOpt.get()
				.findFirst(ClassOrInterfaceDeclaration.class, c -> c.getNameAsString().equals(innerClassName));

		if (!innerClassOpt.isPresent()) {
			throw new IllegalStateException("在外部类中找不到内部类: " + innerClassName);
		}

		// 获取内部类节点
		ClassOrInterfaceDeclaration innerClass = innerClassOpt.get();

		// 创建一个新的编译单元来包含内部类，将内部类转换为普通类
		CompilationUnit newCu = new CompilationUnit();

		// 复制包声明
		cu.getPackageDeclaration().ifPresent(newCu::setPackageDeclaration);

		// 复制所有导入
		for (ImportDeclaration importDecl : cu.getImports()) {
			newCu.addImport(importDecl);
		}

		// 创建新的类声明（将内部类转为普通类）
		ClassOrInterfaceDeclaration newClass = newCu.addClass(innerClassName);

		// 复制内部类的所有内容到新类
		newClass.setJavadocComment(innerClass.getJavadoc().orElse(null));
		innerClass.getExtendedTypes().forEach(newClass::addExtendedType);
		innerClass.getImplementedTypes().forEach(newClass::addImplementedType);
		innerClass.getAnnotations().forEach(newClass::addAnnotation);
		innerClass.getModifiers().forEach(mod -> {
			// 移除static修饰符，因为顶级类不能是static
			if (mod.getKeyword() != com.github.javaparser.ast.Modifier.Keyword.STATIC) {
				newClass.addModifier(mod.getKeyword());
			}
		});

		// 复制所有成员
		innerClass.getMembers().forEach(member -> newClass.addMember(member.clone()));

		// 返回转换后的类字符串
		return newCu.toString();
	}
}