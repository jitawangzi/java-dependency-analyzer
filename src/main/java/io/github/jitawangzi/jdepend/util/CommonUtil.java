package io.github.jitawangzi.jdepend.util;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;

import io.github.jitawangzi.jdepend.config.AppConfig;
import io.github.jitawangzi.jdepend.config.RuntimeConfig;

public class CommonUtil {
	private static Logger log = LoggerFactory.getLogger(CommonUtil.class);
	private static Map<String, CompilationUnit> cuCacheMap = new HashMap<>();
	/**
	 * 获取当前类的全限定名（包含包名）
	 * @param cu 编译单元对象
	 * @return 全限定类名（如：com.example.MyClass）
	 */
	public static String getFullClassName(CompilationUnit cu) {
		// 获取包声明
		String packageName = getPackageName(cu);
		// 获取主类名（简单名称）
		String className = getClassName(cu);
		// 组合包名和类名
		return packageName.isEmpty() ? className : packageName + "." + className;
	}

	/**
	 * 获取当前类的简单类名
	 * @param cu 编译单元对象
	 * @return 
	 */
	public static String getClassName(CompilationUnit cu) {
		// 获取主类名（简单名称）
		String className = cu.getPrimaryTypeName().filter(name -> !name.isEmpty()).orElseGet(() -> {
			// 查找所有类型声明(类、接口、枚举、注解等)
			List<TypeDeclaration> declarations = cu.findAll(TypeDeclaration.class);
			if (!declarations.isEmpty()) {
				return declarations.get(0).getNameAsString();
			}

			// 尝试从文件名推断
			return cu.getStorage()
					.map(storage -> storage.getFileName().replace(".java", ""))
					.orElseThrow(() -> new IllegalStateException("无法获取类名"));
		});

		return className;
	}

	/**
	 * 获取当前包名
	 * @param cu 编译单元对象
	 * @return 
	 */
	public static String getPackageName(CompilationUnit cu) {
		// 获取包声明
		String packageName = cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");
		return packageName;
	}

	public static boolean isSingleLineMethod(MethodDeclaration method) {
		if (method.getBody().isPresent()) {
			BlockStmt body = method.getBody().get();
			// 获取方法体开始和结束行
			int beginLine = body.getBegin().get().line;
			int endLine = body.getEnd().get().line;
			// 实际代码行数(减去花括号占用的行)
			int codeLines = endLine - beginLine - 1;
			return codeLines == 1;
		}
		return false;
	}

	/**
	 * 判断是否是项目内的类
	 * 
	 * @param className 类名
	 * @return 是否是项目内的类
	 */
	public static boolean isProjectClass(String className) {
		Set<String> projectPackagePrefixes = AppConfig.INSTANCE.getProjectPackagePrefixes();
		boolean ret = false;
		for (String prefix : projectPackagePrefixes) {
			if (className.startsWith(prefix)) {
				ret = true;
			}
		}
		if (!ret) {
			return false;
		}
		return !isExcludedPackage(className);
	}

	/**
	 * 判断是否是被排除的包
	 * 
	 * @param className 类名
	 * @return 是否被排除
	 */
	public static boolean isExcludedPackage(String className) {
		return AppConfig.INSTANCE.getExcludedPackages().stream().anyMatch(className::startsWith);
	}

	/**
	 * 判断是否应该保留方法体
	 * 
	 * @param className 类名
	 * @param depth 引用深度
	 * @return 是否应该保留方法体
	 */
	public static boolean shouldKeepMethods(String className, int depth) {
		if (RuntimeConfig.isDirectoryMode) {
			return !AppConfig.INSTANCE.isSimplifyMethods() || AppConfig.INSTANCE.getMethodExceptions().contains(className);
		}
		return AppConfig.INSTANCE.getMethodBodyMaxDepth() < 0 || depth <= AppConfig.INSTANCE.getMethodBodyMaxDepth();
	}

	/**
	 * 收集类级别的依赖（导入、接口、父类等）
	 * 
	 * @param cu 编译单元
	 * @param className 当前类名
	 */
	public static Set<String> collectClassLevelDependencies(CompilationUnit cu, String className) {
		Set<String> allDependencies = new HashSet<>();

		// 1. 从导入语句中收集依赖
		allDependencies.addAll(getProjectImports(cu));

		try {
			// 2. 收集实现的接口和继承的类
			cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
				// 收集实现的接口
				classDecl.getImplementedTypes().forEach(implementedType -> {
					try {
						ResolvedType resolvedType = implementedType.resolve();
						if (resolvedType.isReferenceType()) {
							ResolvedReferenceType referenceType = resolvedType.asReferenceType();
							String qualifiedName = referenceType.getQualifiedName();
							if (CommonUtil.isProjectClass(qualifiedName)) {
								allDependencies.add(qualifiedName);
							}
						}
					} catch (Exception e) {
						// 解析失败时的处理
						log.error("Failed to resolve implemented type: " + implementedType.getNameAsString());
					}
				});

				// 收集继承的类
				classDecl.getExtendedTypes().forEach(extendedType -> {
					try {
						ResolvedType resolvedType = extendedType.resolve();
						if (resolvedType.isReferenceType()) {
							ResolvedReferenceType referenceType = resolvedType.asReferenceType();
							String qualifiedName = referenceType.getQualifiedName();
							if (CommonUtil.isProjectClass(qualifiedName)) {
								allDependencies.add(qualifiedName);
							}
						}

					} catch (Exception e) {
						// 解析失败时的处理
						log.error("Failed to resolve extended type: " + extendedType.getNameAsString());
					}
				});
			});

			// 3. 从字段声明中收集依赖
			cu.findAll(FieldDeclaration.class).forEach(field -> {
				field.getVariables().forEach(var -> {
					try {
						// 解析字段类型
						ResolvedType resolvedType = var.getType().resolve();
						collectTypeAndGenericDependencies(resolvedType, allDependencies);
					} catch (Exception e) {
						// 解析失败时的处理
						log.error("Failed to resolve field type: " + var.getType().asString());
					}
				});
			});

			// 4. 从方法参数和返回类型中收集依赖
			cu.findAll(MethodDeclaration.class).forEach(method -> {
				// 解析返回类型
				if (!method.getType().isVoidType()) {
					try {
						ResolvedType returnType = method.getType().resolve();
						collectTypeAndGenericDependencies(returnType, allDependencies);
					} catch (Exception e) {
						log.error("Failed to resolve return type: " + method.getType().asString());
					}
				}

				// 解析方法参数
				method.getParameters().forEach(param -> {
					try {
						ResolvedType paramType = param.getType().resolve();
						collectTypeAndGenericDependencies(paramType, allDependencies);
					} catch (Exception e) {
						log.error("Failed to resolve parameter type: " + param.getType().asString());
					}
				});

				// 解析抛出的异常
				method.getThrownExceptions().forEach(exception -> {
					try {
						ResolvedType exceptionType = exception.resolve();
						if (exceptionType.isReferenceType()) {
							String qualifiedName = exceptionType.asReferenceType().getQualifiedName();
							if (CommonUtil.isProjectClass(qualifiedName)) {
								allDependencies.add(qualifiedName);
							}
						}
					} catch (Exception e) {
						log.error("Failed to resolve exception type: " + exception.asString());
					}
				});
			});
		} catch (Exception e) {
			log.error("Error during dependency collection: " + e.getMessage());
		}

		return allDependencies;
	}

	/**
	 * 收集类型及其泛型参数的依赖
	 */
	private static void collectTypeAndGenericDependencies(ResolvedType resolvedType, Set<String> dependencies) {
		if (resolvedType.isReferenceType()) {
			ResolvedReferenceType referenceType = resolvedType.asReferenceType();
			String qualifiedName = referenceType.getQualifiedName();

			// 添加主类型
			if (CommonUtil.isProjectClass(qualifiedName)) {
				dependencies.add(qualifiedName);
			}

			// 添加泛型参数类型
			if (referenceType.typeParametersValues().size() > 0) {
				for (ResolvedType typeParameter : referenceType.typeParametersValues()) {
					collectTypeAndGenericDependencies(typeParameter, dependencies);
				}
			}
		} else if (resolvedType.isArray()) {
			// 处理数组类型
			collectTypeAndGenericDependencies(resolvedType.asArrayType().getComponentType(), dependencies);
		}
	}
	/**
	 * 获取项目中的导入类
	 * 
	 * @param className 类名
	 * @return 导入的类集合
	 * @throws Exception 如果获取过程中发生错误
	 */
	public static Set<String> getProjectImports(String className) throws Exception {
		return getProjectImports(parseCompilationUnit(className));
	}

	/**
	 * 获取项目中的导入类
	 * 
	 * @param cu 编译单元
	 * @return 导入的类集合
	 * @throws Exception 如果获取过程中发生错误
	 */
	public static Set<String> getProjectImports(CompilationUnit cu) {
		if (cu == null) {
			log.warn("编译单元为空，无法获取导入类");
			return Set.of(); // 返回空集合而不是null
		}
		return cu.getImports()
				.stream()
				.filter(lambda -> {
					if (lambda.isAsterisk()) {
						// 过滤掉通配符导入
						log.warn("发现通配符导入: {}.* ，暂时没有处理这种依赖", lambda.getNameAsString());
						return false;
					}
					return true;
				})
				.map(ImportDeclaration::getNameAsString)
				.filter(CommonUtil::isProjectClass)
				.collect(Collectors.toSet());
	}
	
	/** 
	 * 通过类名获取到编译单元,带缓存
	 * @param className
	 * @return
	 */
	public static CompilationUnit parseCompilationUnit(String className) {
		// 解析Java文件
		return cuCacheMap.computeIfAbsent(className, k -> {
			try {
				Path file = FileLocator.getInstance().locate(className);
				return StaticJavaParser.parse(file);
			} catch (Exception e) {
				log.error("解析类 {} 时发生错误: {}", className, e.getMessage());
				return null; // 如果解析失败，返回null
			}
		});
	}
}
