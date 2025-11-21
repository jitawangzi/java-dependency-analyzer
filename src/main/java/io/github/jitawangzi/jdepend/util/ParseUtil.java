package io.github.jitawangzi.jdepend.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.SymbolResolver;
import com.github.javaparser.resolution.types.ResolvedType;

@Deprecated
public class ParseUtil {
	/** 
	 *  确定方法调用的作用域,也就是方法所属的类的全限定名
	 * @param cu
	 * @param methodCall
	 * @param currentClassName
	 * @return
	 * @throws IllegalStateException 当无法确定方法调用的作用域时
	 */
	public static String determineScope(CompilationUnit cu, MethodCallExpr methodCall) {

		// 1. 检查是否有明确的作用域
		if (methodCall.getScope().isPresent()) {
			Expression scope = methodCall.getScope().get();
			String scopeStr = scope.toString();

			// 类名识别
			if (scope instanceof NameExpr) {
				String name = ((NameExpr) scope).getNameAsString();
				if (isTypeName(cu, name)) {
					return findFullyQualifiedType(cu, name);
				}
			}

			// 如果作用域是"this"，返回当前类名
			if (scopeStr.equals("this")) {
				return CommonUtil.getFullClassName(cu);
			}

			// 尝试使用符号解析器（如果可用）
			try {
				SymbolResolver symbolResolver = cu.getSymbolResolver();
				if (symbolResolver != null) {
					ResolvedType type = scope.calculateResolvedType();
					if (type.isReferenceType()) {
						return type.asReferenceType().getQualifiedName();
					}
				}
			} catch (Exception e) {
				// 符号解析失败，继续使用其他方法
			}

			// 如果是静态类引用（通常是首字母大写的标识符，没有点号的）
			if (scopeStr.length() > 0 && Character.isUpperCase(scopeStr.charAt(0)) && !scopeStr.contains(".")) {
				// 检查导入以确定全限定名
				for (ImportDeclaration importDecl : cu.getImports()) {
					String importName = importDecl.getNameAsString();
					if (importName.endsWith("." + scopeStr)) {
						return importName;
					}
				}

				// 检查java.lang包中的常用类
				if (isJavaLangClass(scopeStr)) {
					return "java.lang." + scopeStr;
				}

				// 可能是当前包中的类
				if (cu.getPackageDeclaration().isPresent()) {
					return cu.getPackageDeclaration().get().getNameAsString() + "." + scopeStr;
				}

				// 无法确定类的包
				throw new IllegalStateException("无法确定类 " + scopeStr + " 的完整包名");
			}

			// 如果是变量引用（NameExpr）
			if (scope instanceof NameExpr) {
				String varName = ((NameExpr) scope).getNameAsString();

				// 检查是否在lambda表达式中
				Optional<LambdaExpr> lambdaExpr = scope.findAncestor(LambdaExpr.class);
				if (lambdaExpr.isPresent()) {
					// 检查变量是否是lambda参数
					for (Parameter param : lambdaExpr.get().getParameters()) {
						if (param.getNameAsString().equals(varName)) {
							// 尝试推断lambda参数类型
							return InferLambdaParameterTypeResolver.inferLambdaParameterType(cu, lambdaExpr.get(), varName, methodCall);
						}
					}
				}

				// 1. 先检查是否在方法或初始化块中定义的局部变量
				// 检查方法上下文
				Optional<MethodDeclaration> enclosingMethod = methodCall.findAncestor(MethodDeclaration.class);
				if (enclosingMethod.isPresent()) {
					// 检查是否是方法参数
					for (Parameter param : enclosingMethod.get().getParameters()) {
						if (param.getNameAsString().equals(varName)) {
							return getFullyQualifiedType(cu, param.getType());
						}
					}

					// 检查是否是局部变量
					Optional<BlockStmt> body = enclosingMethod.get().getBody();
					if (body.isPresent()) {
						// 查找方法体中声明的变量
						List<VariableDeclarator> localVars = body.get().findAll(VariableDeclarator.class);
						for (VariableDeclarator var : localVars) {
							if (var.getNameAsString().equals(varName)) {
								// 检查变量是否在方法调用之前声明
								if (var.getBegin().isPresent() && methodCall.getBegin().isPresent()
										&& var.getBegin().get().isBefore(methodCall.getBegin().get())) {
									return getFullyQualifiedType(cu, var.getType());
								}
							}
						}
					}
				} else {
					// 检查是否在静态初始化块或实例初始化块中
					Optional<InitializerDeclaration> initBlock = methodCall.findAncestor(InitializerDeclaration.class);
					if (initBlock.isPresent()) {
						// 查找初始化块中声明的变量
						List<VariableDeclarator> localVars = initBlock.get().findAll(VariableDeclarator.class);
						for (VariableDeclarator var : localVars) {
							if (var.getNameAsString().equals(varName)) {
								// 检查变量是否在方法调用之前声明
								if (var.getBegin().isPresent() && methodCall.getBegin().isPresent()
										&& var.getBegin().get().isBefore(methodCall.getBegin().get())) {
									return getFullyQualifiedType(cu, var.getType());
								}
							}
						}
					}
				}

				// 2. 再检查是否是字段
				Optional<FieldDeclaration> field = findFieldByName(cu, varName);
				if (field.isPresent()) {
					return getTypeFromFieldDeclaration(cu, field.get());
				}

				// 无法确定变量类型
				throw new IllegalStateException("无法确定变量 " + varName + " 的类型");
			}

			// 如果是方法调用（链式调用）
			if (scope instanceof MethodCallExpr) {
				MethodCallExpr methodScope = (MethodCallExpr) scope;
				// 递归分析方法调用链
				String parentScope = determineScope(cu, methodScope);

				// 尝试确定方法返回类型
				String methodName = methodScope.getNameAsString();
				return MethodReturnTypeResolver.getMethodReturnType(cu, parentScope, methodName);
			}

			// 如果是字段访问表达式
			if (scope instanceof FieldAccessExpr) {
				FieldAccessExpr fieldAccess = (FieldAccessExpr) scope;
				String fieldName = fieldAccess.getNameAsString();
				Expression fieldScope = fieldAccess.getScope();

				// 递归分析字段作用域
				if (fieldScope instanceof ThisExpr) {
					return CommonUtil.getFullClassName(cu);
//					return getFieldType(cu, currentClassName, fieldName);
				} else {
					String scopeType = "unknown";
					if (fieldScope instanceof NameExpr) {
						String nameAsString = ((NameExpr) fieldScope).getNameAsString();
						// 检查是否是类名（类型）
						if (ParseUtil.isTypeName(cu, nameAsString)) {
							// 直接作为类型处理
							scopeType = ParseUtil.findFullyQualifiedType(cu, nameAsString);
						} else {
							// 作为变量处理
							scopeType = getVarType(cu, nameAsString, methodCall);
						}

//						scopeType = getVarType(cu, nameAsString, methodCall);
					} else {
						throw new IllegalStateException("不支持的字段访问表达式类型: " + fieldScope.getClass().getSimpleName());
					}
					return getFieldType(cu, scopeType, fieldName);
				}
			}
			// 未处理的作用域表达式类型
			throw new IllegalStateException("不支持的作用域表达式类型: " + scope.getClass().getSimpleName() + " [" + scopeStr + "]");
		}

		// 2. 检查是否在lambda表达式中
		Optional<LambdaExpr> lambdaAncestor = methodCall.findAncestor(LambdaExpr.class);
		if (lambdaAncestor.isPresent()) {
			// 检查方法是否是当前类中的方法
			boolean isClassMethod = cu.findAll(com.github.javaparser.ast.body.MethodDeclaration.class)
					.stream()
					.anyMatch(md -> md.getNameAsString().equals(methodCall.getNameAsString()));
			if (isClassMethod) {
				// 这是一个类方法调用
				return CommonUtil.getFullClassName(cu);
			}

			// Lambda表达式上下文中的非类方法调用
			throw new IllegalStateException("无法确定Lambda表达式中方法 " + methodCall.getNameAsString() + " 的作用域");
		}

		// 3. 检查是否在静态初始化块中
		Optional<InitializerDeclaration> initBlock = methodCall.findAncestor(InitializerDeclaration.class);
		if (initBlock.isPresent()) {
			if (initBlock.get().isStatic()) {
				// 在静态初始化块中，检查是否是类的静态方法
				boolean isClassMethod = cu.findAll(com.github.javaparser.ast.body.MethodDeclaration.class)
						.stream()
						.anyMatch(md -> md.isStatic() && md.getNameAsString().equals(methodCall.getNameAsString()));
				if (isClassMethod) {
					return CommonUtil.getFullClassName(cu);
				}

				// 检查静态导入
				for (ImportDeclaration importDecl : cu.getImports()) {
					if (importDecl.isStatic()) {
						String importName = importDecl.getNameAsString();
						if (importName.endsWith("." + methodCall.getNameAsString())) {
							return importName.substring(0, importName.lastIndexOf('.'));
						}
					}
				}

				// 静态初始化块中的未知方法调用
				throw new IllegalStateException("无法确定静态初始化块中方法 " + methodCall.getNameAsString() + " 的作用域");
			} else {
				// 实例初始化块
				boolean isClassMethod = cu.findAll(com.github.javaparser.ast.body.MethodDeclaration.class)
						.stream()
						.anyMatch(md -> md.getNameAsString().equals(methodCall.getNameAsString()));
				if (isClassMethod) {
					return CommonUtil.getFullClassName(cu);
				}

				// 实例初始化块中的未知方法调用
				throw new IllegalStateException("无法确定实例初始化块中方法 " + methodCall.getNameAsString() + " 的作用域");
			}
		}

		// 4. 尝试从类定义中确定
		boolean hasSameClassMethod = cu.findAll(com.github.javaparser.ast.body.MethodDeclaration.class)
				.stream()
				.anyMatch(md -> md.getNameAsString().equals(methodCall.getNameAsString()));
		if (hasSameClassMethod) {
			return CommonUtil.getFullClassName(cu);
		}

		// 5. 检查是否可能是超类方法
		try {
			// 获取当前类的声明
			ClassOrInterfaceDeclaration classDecl = cu.findFirst(ClassOrInterfaceDeclaration.class).orElse(null);
			if (classDecl != null && !classDecl.getExtendedTypes().isEmpty()) {
				// 如果类有继承，可能是继承的方法
				String superClassName = classDecl.getExtendedTypes(0).getNameAsString();

				// 尝试解析超类的全限定名
				for (ImportDeclaration importDecl : cu.getImports()) {
					String importName = importDecl.getNameAsString();
					if (importName.endsWith("." + superClassName)) {
						return importName;
					}
				}

				// 可能在同一个包中
				if (cu.getPackageDeclaration().isPresent()) {
					return cu.getPackageDeclaration().get().getNameAsString() + "." + superClassName;
				}
			}
		} catch (Exception e) {
			// 处理超类分析中的异常
		}
		// Object 类的特殊方法处理
		if (methodCall.getNameAsString().equals("getClass")) {
			return "java.lang.Object";

		}
		// 最终无法确定方法调用的作用域
		throw new IllegalStateException("无法确定方法 " + methodCall.getNameAsString() + " 的作用域");
	}

	/**
	 * 尝试获取变量类型，但不抛出异常
	 */
	public static String tryGetVarType(CompilationUnit cu, String varName, Node context) {
		try {
			return getVarType(cu, varName, context);
		} catch (Exception e) {
			return "java.lang.Object";
		}
	}

	/**
	 * 检查是否在流操作中
	 */
	public static boolean isInsideStreamOperation(Node node) {
		// 检查是否在流操作方法中
		Optional<MethodCallExpr> methodCall = node.findAncestor(MethodCallExpr.class);
		if (methodCall.isPresent()) {
			String methodName = methodCall.get().getNameAsString();
			Set<String> streamOps = new HashSet<>(Arrays.asList("filter", "map", "flatMap", "forEach", "collect", "reduce", "findFirst",
					"findAny", "anyMatch", "allMatch", "noneMatch"));
			return streamOps.contains(methodName);
		}
		return false;
	}

	/**
	 * 查找流操作的集合变量
	 */
	public static String findCollectionVariableForStream(Node node) {
		// 查找调用链中的stream()方法
		Optional<MethodCallExpr> streamCall = findAncestorMethodCall(node, "stream");
		if (!streamCall.isPresent()) {
			streamCall = findAncestorMethodCall(node, "parallelStream");
		}

		if (streamCall.isPresent() && streamCall.get().getScope().isPresent()) {
			Expression scope = streamCall.get().getScope().get();
			if (scope instanceof NameExpr) {
				return ((NameExpr) scope).getNameAsString();
			}
		}

		return null;
	}

	/**
	 * 查找特定名称的祖先方法调用
	 */
	public static Optional<MethodCallExpr> findAncestorMethodCall(Node node, String methodName) {
		Optional<Node> current = Optional.of(node);

		while (current.isPresent()) {
			current = current.get().getParentNode();

			if (current.isPresent() && current.get() instanceof MethodCallExpr) {
				MethodCallExpr methodCall = (MethodCallExpr) current.get();
				if (methodCall.getNameAsString().equals(methodName)) {
					return Optional.of(methodCall);
				}
			}
		}

		return Optional.empty();
	}

	/**
	 * 从集合类型推断元素类型
	 */
	public static String inferCollectionElementType(String collectionType) {

		// 如果是Stream类型，尝试提取泛型参数
		if (collectionType.equals("java.util.stream.Stream") || collectionType.contains("Stream<")) {
			// 提取Stream的泛型参数
			if (collectionType.contains("<") && collectionType.contains(">")) {
				int start = collectionType.indexOf("<") + 1;
				int end = collectionType.lastIndexOf(">");
				if (start < end) {
					return collectionType.substring(start, end).trim();
				}
			}
			// 无法确定Stream的元素类型，返回Object
			return "java.lang.Object";
		}

		// 如果类型包含泛型信息，尝试提取
		if (collectionType.contains("<") && collectionType.contains(">")) {
			int start = collectionType.indexOf("<") + 1;
			int end = collectionType.lastIndexOf(">");
			if (start < end) {
				String genericType = collectionType.substring(start, end).trim();
				// 处理多个泛型参数的情况 (如Map<K,V>)
				if (genericType.contains(",")) {
					// 对于Map的情况，通常我们需要第二个泛型参数
					if (collectionType.startsWith("java.util.Map") || collectionType.endsWith("Map>")) {
						String[] params = genericType.split(",");
						if (params.length > 1) {
							return params[1].trim();
						}
					} else {
						// 对于其他情况，取第一个参数
						return genericType.split(",")[0].trim();
					}
				}
				return genericType;
			}
		}

		// 对于已知集合类型的默认元素类型
		if (collectionType.contains("List") || collectionType.contains("Set") || collectionType.contains("Collection")) {
			return "java.lang.Object";
		}

		// 特定于依赖分析的情况
		if (collectionType.contains("dependencies")) {
			return "com.yourpackage.ClassDependency"; // 调整为实际包名
		}

		return "java.lang.Object";
	}

	/**
	 * 基于方法名和参数位置推断函数式接口参数类型
	 *//*
		public static String inferFunctionalInterfaceParameterType(String callerType, String methodName, int paramIndex) {
		// 常见的Java 8 Stream API方法参数类型
		if (methodName.equals("filter")) {
			return "java.lang.Object"; // Predicate<T>的T
		} else if (methodName.equals("map")) {
			return "java.lang.Object"; // Function<T,R>的T
		} else if (methodName.equals("forEach")) {
			return "java.lang.Object"; // Consumer<T>的T
		}
		
		// 对于特定的调用，可以添加更精确的类型推断
		if (callerType.contains("List") || callerType.contains("Set")) {
			return inferCollectionElementType(callerType);
		}
		
		
		return "java.lang.Object";
		}*/

	/**
	 * 获取变量的类型
	 * @throws IllegalStateException 如果无法确定变量类型
	 */
	public static String getVarType(CompilationUnit cu, String varName, Node context) {

		// 处理包含点号的变量名，例如 AppConfigManager.get()
		if (varName.contains(".")) {
			String[] parts = varName.split("\\.", 2);
			String typeName = parts[0];
			String memberName = parts[1];

			// 查找类型的完全限定名
			String fullyQualifiedTypeName = findFullyQualifiedType(cu, typeName);

			// 特殊处理接口的静态实例
			if (memberName.equals("INSTANCE")) {
				// 对于接口中的静态实例，返回接口类型本身
				return fullyQualifiedTypeName;
			}

			// 对于其他成员，可能需要更复杂的解析
			// 简化处理：返回一个假设的类型
			return "java.lang.Object";
		}

		// 1. 检查局部变量和参数
		Optional<MethodDeclaration> enclosingMethod = context.findAncestor(MethodDeclaration.class);
		if (enclosingMethod.isPresent()) {
			// 检查参数
			for (Parameter param : enclosingMethod.get().getParameters()) {
				if (param.getNameAsString().equals(varName)) {
					return getFullyQualifiedType(cu, param.getType());
				}
			}

			// 检查局部变量
			Optional<BlockStmt> body = enclosingMethod.get().getBody();
			if (body.isPresent()) {
				List<VariableDeclarator> localVars = body.get().findAll(VariableDeclarator.class);
				for (VariableDeclarator var : localVars) {
					if (var.getNameAsString().equals(varName)) {
						// 确保变量在使用前声明
						if (var.getBegin().isPresent() && context.getBegin().isPresent()
								&& var.getBegin().get().isBefore(context.getBegin().get())) {
							return getFullyQualifiedType(cu, var.getType());
						}
					}
				}
			}
		} else {
			// 检查初始化块中的变量
			Optional<InitializerDeclaration> initBlock = context.findAncestor(InitializerDeclaration.class);
			if (initBlock.isPresent()) {
				List<VariableDeclarator> localVars = initBlock.get().findAll(VariableDeclarator.class);
				for (VariableDeclarator var : localVars) {
					if (var.getNameAsString().equals(varName)) {
						if (var.getBegin().isPresent() && context.getBegin().isPresent()
								&& var.getBegin().get().isBefore(context.getBegin().get())) {
							return getFullyQualifiedType(cu, var.getType());
						}
					}
				}
			}
		}

		// 2. 检查字段
		Optional<FieldDeclaration> field = findFieldByName(cu, varName);
		if (field.isPresent()) {
			return getTypeFromFieldDeclaration(cu, field.get());
		}

		// 3. 检查lambda参数
		Optional<LambdaExpr> lambdaExpr = context.findAncestor(LambdaExpr.class);
		if (lambdaExpr.isPresent()) {
			for (Parameter param : lambdaExpr.get().getParameters()) {
				if (param.getNameAsString().equals(varName)) {
					if (param.getType().isUnknownType()) {
						// 尝试推断lambda参数类型
						return InferLambdaParameterTypeResolver.inferLambdaParameterType(cu, lambdaExpr.get(), varName, context);
					} else {
						return getFullyQualifiedType(cu, param.getType());
					}
				}
			}
		}

		throw new IllegalStateException("无法确定变量 " + varName + " 的类型");
	}

	/**
	 * 
	 */
	/** 
	 * 从某个编译单元中，获取某个类的全限定名
	 * @param cu  编译单元，可能需要其中的import来确定类的完整包名+类名
	 * @param typeName
	 * @return
	 */
	private static String findFullyQualifiedType(CompilationUnit cu, String typeName) {
		// 1. 检查显式导入
		Optional<ImportDeclaration> importDecl = cu.getImports()
				.stream()
				.filter(imp -> !imp.isAsterisk() && imp.getNameAsString().endsWith("." + typeName))
				.findFirst();

		if (importDecl.isPresent()) {
			return importDecl.get().getNameAsString();
		}

		// 2. 检查当前编译单元中的类型声明
		Optional<ClassOrInterfaceDeclaration> typeDecl = cu.findAll(ClassOrInterfaceDeclaration.class)
				.stream()
				.filter(decl -> decl.getNameAsString().equals(typeName))
				.findFirst();

		if (typeDecl.isPresent()) {
			String packageName = cu.getPackageDeclaration().map(p -> p.getNameAsString() + ".").orElse("");
			return packageName + typeName;
		}

		// 3. 检查java.lang包
		if (isJavaLangClass(typeName)) {
			return "java.lang." + typeName;
		}

		// 4. 假设在当前包中
		return cu.getPackageDeclaration().map(pkg -> pkg.getNameAsString() + "." + typeName).orElse(typeName);
	}

	/**
	 * 检查是否为类型名称（类或接口）
	 */
	private static boolean isTypeName(CompilationUnit cu, String name) {
		// 检查是否为导入的类型
		boolean isImported = cu.getImports().stream().anyMatch(imp -> imp.getNameAsString().endsWith("." + name));

		if (isImported) {
			return true;
		}

		// 检查是否为当前编译单元中声明的类型
		boolean isDeclaredInCU = cu.findAll(ClassOrInterfaceDeclaration.class)
				.stream()
				.anyMatch(decl -> decl.getNameAsString().equals(name));

		return isDeclaredInCU;
	}

	/** 
	 * 获取字段类型
	 * @param cu
	 * @param fullClassName
	 * @param fieldName
	 * @return
	 */
	public static String getFieldType(CompilationUnit cu, String fullClassName, String fieldName) {

		// 1. 尝试符号解析器（首选）
		try {
			SymbolResolver symbolResolver = cu == null ? null : cu.getSymbolResolver();
			if (symbolResolver != null) {
				// 使用符号解析器解析字段的类型
				Optional<ClassOrInterfaceDeclaration> classDeclaration = cu.findFirst(ClassOrInterfaceDeclaration.class,
						c -> fullClassName.endsWith(c.getNameAsString()));
				if (classDeclaration.isPresent()) {
					ClassOrInterfaceDeclaration classDecl = classDeclaration.get();
					Optional<FieldDeclaration> field = classDecl.getFields()
							.stream()
							.filter(f -> f.getVariables().stream().anyMatch(v -> v.getNameAsString().equals(fieldName)))
							.findFirst();
					if (field.isPresent()) {
						ResolvedType resolvedType = field.get().getElementType().resolve();
						return resolvedType.describe();
					}
				}
			}
		} catch (Exception e) {
			// 符号解析失败，继续尝试其他方法
		}

		// 2. 特殊处理单例模式
		if ("INSTANCE".equals(fieldName) || "instance".equals(fieldName)) {
			// 假设INSTANCE是单例模式的静态实例
			return cu != null ? CommonUtil.getFullClassName(cu) : fullClassName;
		}

		// 3. 处理当前编译单元中的字段
		if (fullClassName.equals(CommonUtil.getFullClassName(cu))) {
			Optional<FieldDeclaration> field = findFieldByName(cu, fieldName);
			if (field.isPresent()) {
				return getTypeFromFieldDeclaration(cu, field.get());
			}
		}

		// 4. 尝试通过类名和字段名推断
		String inferredType = inferFieldType(fullClassName, fieldName);
		if (inferredType != null) {
			return inferredType;
		}

		// 5. 最终后备方案
		throw new IllegalStateException("无法确定字段 " + fullClassName + "." + fieldName + " 的类型");
	}

	/**
	 * 基于常见命名规则推断字段类型
	 */
	private static String inferFieldType(String className, String fieldName) {
		// 处理日志记录器
		if ("logger".equalsIgnoreCase(fieldName) || "log".equalsIgnoreCase(fieldName)) {
			return "org.slf4j.Logger";
		}

		// 处理配置实例
		if (fieldName.endsWith("Config") || fieldName.endsWith("Properties")) {
			return className.substring(0, className.lastIndexOf('.')) + "." + fieldName;
		}

		// 处理常量字段
		if (fieldName.equals(fieldName.toUpperCase())) {
			// 可能是基本类型常量
			return "java.lang.String";
		}

		// 处理集合类型
		if (fieldName.endsWith("List") || fieldName.endsWith("s")) {
			return "java.util.List";
		}

		return null;
	}

	/**
	 * 在编译单元中查找指定名称的字段
	 */
	public static Optional<FieldDeclaration> findFieldByName(CompilationUnit cu, String fieldName) {
		// 获取当前类中定义的所有字段
		List<FieldDeclaration> fields = new ArrayList<>();

		// 查找主类和内部类的所有字段
		for (TypeDeclaration<?> type : cu.getTypes()) {
			fields.addAll(type.getFields());
		}

		// 查找匹配名称的字段
		for (FieldDeclaration field : fields) {
			for (VariableDeclarator variable : field.getVariables()) {
				if (variable.getNameAsString().equals(fieldName)) {
					return Optional.of(field);
				}
			}
		}

		return Optional.empty();
	}

	/**
	 * 从字段声明中获取类型信息
	 */
	public static String getTypeFromFieldDeclaration(CompilationUnit cu, FieldDeclaration field) {
		// 获取第一个变量声明器的类型
		Type type = field.getVariable(0).getType();
		return getFullyQualifiedType(cu, type);
	}

	/**
	 * 获取类型的全限定名
	 */
	public static String getFullyQualifiedType(CompilationUnit cu, Type type) {
		String typeStr = type.asString();

		// 尝试解析完整类型
		try {
			if (cu.getSymbolResolver() != null) {
				ResolvedType resolvedType = type.resolve();
				if (resolvedType.isReferenceType()) {
					return resolvedType.asReferenceType().getQualifiedName();
				}
			}
		} catch (Exception e) {
			// 解析失败，使用类型字符串
		}

		// 如果是原始类型，直接返回
		if (type.isPrimitiveType()) {
			return typeStr;
		}

		// 处理引用类型
		if (type.isClassOrInterfaceType()) {
			ClassOrInterfaceType classType = type.asClassOrInterfaceType();
			String className = classType.getNameAsString();

			// 移除泛型部分
			if (className.contains("<")) {
				className = className.substring(0, className.indexOf('<'));
			}

			// 如果已经是全限定名，直接返回
			if (className.contains(".")) {
				return className;
			}

			// 检查导入语句
			for (ImportDeclaration importDecl : cu.getImports()) {
				String importName = importDecl.getNameAsString();

				// 检查明确的导入
				if (importName.endsWith("." + className)) {
					return importName;
				}

				// 检查星号导入
				if (importName.endsWith(".*")) {
					String packageName = importName.substring(0, importName.length() - 2);
					// 可能在此包中
					// 注意：这需要类路径扫描才能确定
				}
			}

			// 检查java.lang包中的常用类
			if (isJavaLangClass(className)) {
				return "java.lang." + className;
			}

			// 检查是否在同一包中
			if (cu.getPackageDeclaration().isPresent()) {
				String packageName = cu.getPackageDeclaration().get().getNameAsString();
				return packageName + "." + className;
			}
		}

		// 无法解析完整类型，返回原始类型字符串
		return typeStr;
	}

	/**
	 * 检查给定的类名是否是java.lang包中的常用类
	 */
	public static boolean isJavaLangClass(String className) {
		Set<String> javaLangClasses = new HashSet<>(
				Arrays.asList("String", "Integer", "Long", "Double", "Float", "Boolean", "Byte", "Character", "Short", "Object", "Class",
						"Enum", "System", "Thread", "Throwable", "Error", "Exception", "RuntimeException", "Iterable", "Comparable"));
		return javaLangClasses.contains(className);
	}

}
