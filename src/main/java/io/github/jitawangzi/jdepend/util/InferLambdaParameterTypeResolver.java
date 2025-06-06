package io.github.jitawangzi.jdepend.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.CharLiteralExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.TypeExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;

@Deprecated
public class InferLambdaParameterTypeResolver {

	/**
	 * 推断lambda表达式参数的类型
	 * @param cu 编译单元
	 * @param lambdaExpr Lambda表达式
	 * @param paramName 参数名称
	 * @param context 上下文节点
	 * @return 推断的参数类型
	 */
	public static String inferLambdaParameterType(CompilationUnit cu, LambdaExpr lambdaExpr, String paramName, Node context) {
		// 1. 首先检查lambda是否有显式参数类型
		for (Parameter param : lambdaExpr.getParameters()) {
			if (param.getNameAsString().equals(paramName) && param.getType() != null && !param.getType().isUnknownType()) {
				return param.getType().asString();
			}
		}

		// 获取参数索引
		int paramIndex = getParameterIndex(lambdaExpr, paramName);
		if (paramIndex < 0) {
			// 如果参数不存在，返回Object
			return "java.lang.Object";
		}

		// 2. 尝试从变量声明上下文推断类型
		Optional<VariableDeclarator> varDeclContext = lambdaExpr.findAncestor(VariableDeclarator.class);
		if (varDeclContext.isPresent()) {
			VariableDeclarator vd = varDeclContext.get();
			String varType = vd.getType().asString();

			// 处理常见的函数式接口
			if (isFunctionalInterface(varType)) {
				String inferredType = inferFunctionalInterfaceParameterType(varType, paramIndex);
				if (!inferredType.equals("java.lang.Object")) {
					return inferredType;
				}
			}
		}

		// 3. 尝试确定lambda表达式的方法调用上下文
		Optional<MethodCallExpr> parentCall = lambdaExpr.findAncestor(MethodCallExpr.class);
		if (parentCall.isPresent()) {
			MethodCallExpr call = parentCall.get();
			String methodName = call.getNameAsString();

			// 确定调用方法的对象类型
			String callerType = "unknown";
			if (call.getScope().isPresent()) {
				try {
					Expression callerExpr = call.getScope().get();
					if (callerExpr instanceof NameExpr) {
						// 尝试确定变量类型
						callerType = ParseUtil.tryGetVarType(cu, ((NameExpr) callerExpr).getNameAsString(), call);
					} else if (callerExpr instanceof MethodCallExpr) {
						// 处理方法调用链
						callerType = inferMethodReturnType((MethodCallExpr) callerExpr, cu);
					} else {
						// 处理其他类型的表达式
						callerType = inferExpressionType(callerExpr, cu);
					}
				} catch (Exception e) {
					// 忽略异常，继续尝试其他方法
				}
			}

			// 3.1 推断流操作中的元素类型
			if (methodName.equals("stream") || methodName.equals("parallelStream")) {
				// 获取集合类型的泛型参数
				return inferCollectionElementType(callerType);
			} else if (isStreamIntermediateOperation(methodName)) {
				// 中间操作（filter, map, flatMap等）
				return inferStreamElementType(callerType, cu, call);
			} else if (isStreamTerminalOperation(methodName)) {
				// 终端操作（forEach, collect, reduce等）
				int argPosition = getParameterPosition(call, lambdaExpr);

				if (methodName.equals("reduce") || methodName.equals("collect")) {
					return inferReduceOrCollectParameterType(callerType, methodName, argPosition, cu, call);
				} else {
					return inferStreamElementType(callerType, cu, call);
				}
			}

			// 3.2 尝试从方法上下文中确定类型
			String paramType = inferFunctionalInterfaceParameterType(callerType, methodName, paramIndex);
			if (!paramType.equals("java.lang.Object")) {
				return paramType;
			}

			// 3.3 尝试根据方法参数位置推断类型
			int argPosition = getParameterPosition(call, lambdaExpr);
			if (argPosition >= 0) {
				try {
					String inferredType = inferParameterTypeFromMethodSignature(callerType, methodName, argPosition, paramIndex, cu);
					if (!inferredType.equals("java.lang.Object")) {
						return inferredType;
					}
				} catch (Exception e) {
					// 忽略异常，继续尝试其他方法
				}
			}

			// 3.4 处理常见的方法调用模式
			String inferredType = inferFromCommonMethodPatterns(call, lambdaExpr, paramName, paramIndex, cu);
			if (!inferredType.equals("unknown")) {
				return inferredType;
			}
		}

		// 4. 处理赋值表达式上下文
		Optional<AssignExpr> assignContext = lambdaExpr.findAncestor(AssignExpr.class);
		if (assignContext.isPresent()) {
			AssignExpr assignExpr = assignContext.get();
			if (assignExpr.getValue() == lambdaExpr) {
				// Lambda表达式被赋值给某个变量
				Expression target = assignExpr.getTarget();
				if (target instanceof NameExpr) {
					String varName = ((NameExpr) target).getNameAsString();
					String varType = ParseUtil.tryGetVarType(cu, varName, assignExpr);

					// 如果目标变量是函数式接口，推断参数类型
					if (!varType.equals("unknown")) {
						String inferredType = inferFunctionalInterfaceParameterType(varType, paramIndex);
						if (!inferredType.equals("java.lang.Object")) {
							return inferredType;
						}
					}
				}
			}
		}

		// 5. 基于流操作和上下文的常见假设
		if (ParseUtil.isInsideStreamOperation(lambdaExpr)) {
			// 5.1 确定变量来自哪个集合
			String collectionVar = ParseUtil.findCollectionVariableForStream(lambdaExpr);
			if (collectionVar != null) {
				String collectionType = ParseUtil.tryGetVarType(cu, collectionVar, lambdaExpr);
				if (!collectionType.equals("unknown")) {
					return ParseUtil.inferCollectionElementType(collectionType);
				}
			}

			// 5.2 尝试从整个流操作链中推断类型
			Optional<MethodCallExpr> streamSource = findStreamSourceMethod(lambdaExpr);
			if (streamSource.isPresent()) {
				return inferStreamElementTypeFromChain(streamSource.get(), cu);
			}
		}

		// 6. 查找常见的函数式接口上下文
		String functionalType = inferFromCommonFunctionalContexts(lambdaExpr, cu, paramName);
		if (!functionalType.equals("unknown")) {
			return functionalType;
		}

		// 7. 处理方法引用上下文
		Optional<MethodReferenceExpr> methodRefContext = lambdaExpr.findAncestor(MethodReferenceExpr.class);
		if (methodRefContext.isPresent()) {
			MethodReferenceExpr methodRef = methodRefContext.get();
			// 尝试从方法引用中推断类型
			String inferredType = inferTypeFromMethodReference(methodRef, cu, paramIndex);
			if (!inferredType.equals("unknown")) {
				return inferredType;
			}
		}

		// 8. 处理嵌套的Lambda表达式
		Optional<LambdaExpr> outerLambda = lambdaExpr.findAncestor(LambdaExpr.class);
		if (outerLambda.isPresent()) {
			String inferredType = inferNestedLambdaParameterType(outerLambda.get(), lambdaExpr, paramName, cu);
			if (!inferredType.equals("unknown")) {
				return inferredType;
			}
		}

		// 9. 基于Lambda体内的方法调用推断类型
		String inferredType = inferFromLambdaBodyMethodCalls(lambdaExpr, paramName, cu);
		if (!inferredType.equals("unknown")) {
			return inferredType;
		}

		// 10. 返回Object作为后备
		return "java.lang.Object";
	}

	/**
	 * 判断一个类型是否为函数式接口
	 */
	private static boolean isFunctionalInterface(String typeName) {
		// 常见的函数式接口列表
		Set<String> commonFunctionalInterfaces = new HashSet<>(
				Arrays.asList("java.util.function.Function", "java.util.function.BiFunction", "java.util.function.Consumer",
						"java.util.function.BiConsumer", "java.util.function.Predicate", "java.util.function.BiPredicate",
						"java.util.function.Supplier", "java.util.Comparator", "java.lang.Runnable", "java.util.concurrent.Callable"));

		// 简单检查是否包含常见函数式接口
		for (String fi : commonFunctionalInterfaces) {
			if (typeName.contains(fi) || typeName.endsWith(fi.substring(fi.lastIndexOf('.') + 1))) {
				return true;
			}
		}

		// 也可以检查一些常见模式，如名称以"Consumer"、"Function"、"Predicate"等结尾
		String simpleName = typeName.contains(".") ? typeName.substring(typeName.lastIndexOf('.') + 1) : typeName;
		return simpleName.endsWith("Consumer") || simpleName.endsWith("Function") || simpleName.endsWith("Predicate")
				|| simpleName.endsWith("Supplier") || simpleName.endsWith("Callback") || simpleName.endsWith("Listener")
				|| simpleName.endsWith("Handler");
	}

	/**
	 * 判断是否为流的中间操作
	 */
	private static boolean isStreamIntermediateOperation(String methodName) {
		Set<String> intermediateOps = new HashSet<>(Arrays.asList("filter", "map", "flatMap", "distinct", "sorted", "peek", "limit", "skip",
				"parallel", "sequential", "unordered"));
		return intermediateOps.contains(methodName);
	}

	/**
	 * 判断是否为流的终端操作
	 */
	private static boolean isStreamTerminalOperation(String methodName) {
		Set<String> terminalOps = new HashSet<>(Arrays.asList("forEach", "forEachOrdered", "toArray", "reduce", "collect", "min", "max",
				"count", "anyMatch", "allMatch", "noneMatch", "findFirst", "findAny"));
		return terminalOps.contains(methodName);
	}

	/**
	 * 推断方法调用的返回类型
	 */
	private static String inferMethodReturnType(MethodCallExpr methodCall, CompilationUnit cu) {
		String methodName = methodCall.getNameAsString();

		// 处理常见的流方法
		if (methodName.equals("stream") || methodName.equals("parallelStream")) {
			// 这些方法返回Stream<E>，其中E是集合元素类型
			if (methodCall.getScope().isPresent()) {
				Expression scope = methodCall.getScope().get();
				if (scope instanceof NameExpr) {
					String collectionVar = ((NameExpr) scope).getNameAsString();
					String collectionType = ParseUtil.tryGetVarType(cu, collectionVar, methodCall);
					return "java.util.stream.Stream<" + ParseUtil.inferCollectionElementType(collectionType) + ">";
				}
			}
			return "java.util.stream.Stream<?>";
		} else if (methodName.equals("filter")) {
			// filter返回相同元素类型的流
			return getCallerType(methodCall, cu);
		} else if (methodName.equals("map") || methodName.equals("flatMap")) {
			// 对于map和flatMap，需要推断转换后的类型，这比较复杂
			// 简化实现，保持同一类型
			return getCallerType(methodCall, cu);
		} else if (methodName.equals("collect")) {
			// collect可能返回各种集合类型
			return inferCollectReturnType(methodCall, cu);
		}

		// 其他方法，尝试从类型信息中查找
		String callerType = "unknown";
		if (methodCall.getScope().isPresent()) {
			Expression callerExpr = methodCall.getScope().get();
			if (callerExpr instanceof NameExpr) {
				callerType = ParseUtil.tryGetVarType(cu, ((NameExpr) callerExpr).getNameAsString(), methodCall);
			} else if (callerExpr instanceof MethodCallExpr) {
				callerType = inferMethodReturnType((MethodCallExpr) callerExpr, cu);
			}
		}
		// 反射查找方法返回类型
		return ReflectionUtils.getMethodReturnTypeWithGeneric(callerType, methodName);
		// 对于复杂情况，可以通过类型解析或符号表查找
		// 简化实现，假设一些常见的方法返回类型
//		return inferCommonMethodReturnType(callerType, methodName);
	}

	/**
	 * 获取方法调用者的类型
	 */
	private static String getCallerType(MethodCallExpr methodCall, CompilationUnit cu) {
		if (methodCall.getScope().isPresent()) {
			Expression callerExpr = methodCall.getScope().get();
			if (callerExpr instanceof NameExpr) {
				return ParseUtil.tryGetVarType(cu, ((NameExpr) callerExpr).getNameAsString(), methodCall);
			} else if (callerExpr instanceof MethodCallExpr) {
				return inferMethodReturnType((MethodCallExpr) callerExpr, cu);
			}
		}
		return "unknown";
	}

	/**
	 * 推断collect方法的返回类型
	 */
	private static String inferCollectReturnType(MethodCallExpr methodCall, CompilationUnit cu) {
		// 检查是否使用了Collectors工具类
		if (methodCall.getArguments().size() > 0) {
			Expression arg = methodCall.getArguments().get(0);
			if (arg instanceof MethodCallExpr) {
				MethodCallExpr collectorCall = (MethodCallExpr) arg;
				String collectorMethod = collectorCall.getNameAsString();

				// 推断常见的Collectors方法
				if (collectorMethod.equals("toList")) {
					String elementType = inferStreamElementType(getCallerType(methodCall, cu), cu, methodCall);
					return "java.util.List<" + elementType + ">";
				} else if (collectorMethod.equals("toSet")) {
					String elementType = inferStreamElementType(getCallerType(methodCall, cu), cu, methodCall);
					return "java.util.Set<" + elementType + ">";
				} else if (collectorMethod.equals("toMap")) {
					// 简化实现
					return "java.util.Map<?, ?>";
				}
			}
		}

		// 默认假设为List
		String elementType = inferStreamElementType(getCallerType(methodCall, cu), cu, methodCall);
		return "java.util.List<" + elementType + ">";
	}

	/**
	 * 推断常见方法的返回类型
	 */
	private static String inferCommonMethodReturnType(String callerType, String methodName) {
		// 处理一些常见的方法
		if (methodName.equals("get") || methodName.equals("findFirst") || methodName.equals("findAny")) {
			// 对于集合或Optional的get方法，返回元素类型
			if (callerType.contains("Optional<")) {
				// 提取Optional中的泛型参数
				return extractGenericTypeParameter(callerType);
			} else if (callerType.contains("List<") || callerType.contains("Set<") || callerType.contains("Collection<")
					|| callerType.contains("Iterable<")) {
				// 提取集合中的泛型参数
				return extractGenericTypeParameter(callerType);
			}
		} else if (methodName.equals("getKey")) {
			// Map.Entry的getKey方法
			if (callerType.contains("Entry<")) {
				String[] params = extractGenericTypeParameters(callerType);
				if (params.length > 0) {
					return params[0];
				}
			}
		} else if (methodName.equals("getValue")) {
			// Map.Entry的getValue方法
			if (callerType.contains("Entry<")) {
				String[] params = extractGenericTypeParameters(callerType);
				if (params.length > 1) {
					return params[1];
				}
			}
		}

		// 默认返回Object
		return "java.lang.Object";
	}

	/**
	 * 提取泛型类型参数
	 */
	private static String extractGenericTypeParameter(String genericType) {
		// 简单实现，更完整的实现需要处理嵌套泛型
		if (genericType.contains("<") && genericType.contains(">")) {
			int start = genericType.indexOf('<');
			int end = genericType.lastIndexOf('>');
			if (start < end) {
				return genericType.substring(start + 1, end).trim();
			}
		}
		throw new IllegalArgumentException("Invalid generic type: " + genericType);
	}

	/**
	 * 提取多个泛型类型参数
	 */
	private static String[] extractGenericTypeParameters(String genericType) {
		if (genericType.contains("<") && genericType.contains(">")) {
			int start = genericType.indexOf('<');
			int end = genericType.lastIndexOf('>');
			if (start < end) {
				String params = genericType.substring(start + 1, end).trim();
				// 简单拆分，不处理嵌套泛型的情况
				return params.split("\\s*,\\s*");
			}
		}
		return new String[0];
	}

	/**
	 * 推断流元素类型
	 */
	private static String inferStreamElementType(String streamType, CompilationUnit cu, Node context) {
		if (streamType.contains("Stream<") || streamType.contains("Optional<")) {
			// 提取Stream中的泛型参数
			return extractGenericTypeParameter(streamType);
		} else if (streamType.contains("List<") || streamType.contains("Set<") || streamType.contains("Collection<")
				|| streamType.contains("Iterable<")) {
			// 如果是集合类型，返回集合元素类型
			return ParseUtil.inferCollectionElementType(streamType);
		}

		// 尝试从上下文中推断
		Optional<MethodCallExpr> streamSource = findStreamSourceMethod(context);
		if (streamSource.isPresent()) {
			MethodCallExpr sourceCall = streamSource.get();
			if (sourceCall.getScope().isPresent()) {
				Expression scope = sourceCall.getScope().get();
				if (scope instanceof NameExpr) {
					String collectionVar = ((NameExpr) scope).getNameAsString();
					String collectionType = ParseUtil.tryGetVarType(cu, collectionVar, context);
					return ParseUtil.inferCollectionElementType(collectionType);
				}
			}
		}

		// 默认返回Object
		return "java.lang.Object";
	}

	/**
	 * 查找流操作链的源方法
	 */
	private static Optional<MethodCallExpr> findStreamSourceMethod(Node node) {
		Optional<MethodCallExpr> currentCall = node.findAncestor(MethodCallExpr.class);
		while (currentCall.isPresent()) {
			MethodCallExpr call = currentCall.get();
			String methodName = call.getNameAsString();

			if (methodName.equals("stream") || methodName.equals("parallelStream") || methodName.equals("of")
					|| methodName.equals("generate") || methodName.equals("iterate") || methodName.equals("concat")) {
				return currentCall;
			}

			// 继续向上查找
			currentCall = call.findAncestor(MethodCallExpr.class);
		}

		return Optional.empty();
	}

	/**
	 * 从流操作链中推断元素类型
	 */
	private static String inferStreamElementTypeFromChain(MethodCallExpr streamSource, CompilationUnit cu) {
		String methodName = streamSource.getNameAsString();

		if (methodName.equals("stream") || methodName.equals("parallelStream")) {
			// 从集合创建的流
			if (streamSource.getScope().isPresent()) {
				Expression scope = streamSource.getScope().get();
				if (scope instanceof NameExpr) {
					String collectionVar = ((NameExpr) scope).getNameAsString();
					String collectionType = ParseUtil.tryGetVarType(cu, collectionVar, streamSource);
					return ParseUtil.inferCollectionElementType(collectionType);
				}
			}
		} else if (methodName.equals("of")) {
			// Stream.of方法
			if (streamSource.getArguments().size() > 0) {
				// 尝试从第一个参数推断类型
				Expression firstArg = streamSource.getArguments().get(0);
				return inferExpressionType(firstArg, cu);
			}
		} else if (methodName.equals("generate") || methodName.equals("iterate")) {
			// 这些方法通常使用Lambda表达式，推断更复杂
			return "java.lang.Object";
		}

		// 默认返回Object
		return "java.lang.Object";
	}

	/**
	 * 推断表达式的类型
	 */
	private static String inferExpressionType(Expression expr, CompilationUnit cu) {
		if (expr instanceof StringLiteralExpr) {
			return "java.lang.String";
		} else if (expr instanceof IntegerLiteralExpr) {
			return "int";
		} else if (expr instanceof LongLiteralExpr) {
			return "long";
		} else if (expr instanceof DoubleLiteralExpr) {
			return "double";
		} else if (expr instanceof BooleanLiteralExpr) {
			return "boolean";
		} else if (expr instanceof CharLiteralExpr) {
			return "char";
		} else if (expr instanceof NullLiteralExpr) {
			return "java.lang.Object";
		} else if (expr instanceof NameExpr) {
			return ParseUtil.tryGetVarType(cu, ((NameExpr) expr).getNameAsString(), expr);
		} else if (expr instanceof MethodCallExpr) {
			return inferMethodReturnType((MethodCallExpr) expr, cu);
		}

		// 对于其他类型的表达式，可能需要更复杂的分析
		return "java.lang.Object";
	}

	/**
	 * 获取Lambda表达式在方法调用中的参数位置
	 */
	private static int getParameterPosition(MethodCallExpr call, LambdaExpr lambdaExpr) {
		NodeList<Expression> arguments = call.getArguments();
		for (int i = 0; i < arguments.size(); i++) {
			if (arguments.get(i) == lambdaExpr) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * 推断reduce或collect操作的参数类型
	 */
	private static String inferReduceOrCollectParameterType(String callerType, String methodName, int paramPos, CompilationUnit cu,
			Node context) {
		if (methodName.equals("reduce")) {
			// reduce操作的Lambda参数通常接收两个相同类型的参数
			String elementType = inferStreamElementType(callerType, cu, context);
			return elementType;
		} else if (methodName.equals("collect")) {
			// collect操作的参数类型取决于所使用的Collector
			// 简化实现
			return "java.lang.Object";
		}

		return "java.lang.Object";
	}

	/**
	 * 从方法签名中推断参数类型
	 */
	private static String inferParameterTypeFromMethodSignature(String callerType, String methodName, int argPosition, int paramIndex,
			CompilationUnit cu) {
		// 处理常见的Stream方法
		if (callerType.contains("Stream")) {
			String elementType = extractGenericTypeParameter(callerType);

			if (methodName.equals("filter")) {
				// filter接受Predicate<T>
				return elementType;
			} else if (methodName.equals("map")) {
				// map接受Function<T, R>
				return elementType;
			} else if (methodName.equals("flatMap")) {
				// flatMap接受Function<T, Stream<R>>
				return elementType;
			} else if (methodName.equals("forEach")) {
				// forEach接受Consumer<T>
				return elementType;
			}
		}

		// 处理常见的Collection方法
		if (callerType.contains("List<") || callerType.contains("Set<") || callerType.contains("Collection<")) {
			String elementType = ParseUtil.inferCollectionElementType(callerType);

			if (methodName.equals("forEach")) {
				// forEach接受Consumer<T>
				return elementType;
			} else if (methodName.equals("removeIf")) {
				// removeIf接受Predicate<T>
				return elementType;
			}
		}

		// 处理Map的方法
		if (callerType.contains("Map<")) {
			String[] mapTypes = extractGenericTypeParameters(callerType);
			if (mapTypes.length >= 2) {
				if (methodName.equals("computeIfAbsent") || methodName.equals("computeIfPresent") || methodName.equals("compute")) {
					// 第一个参数是键类型
					if (paramIndex == 0) {
						return mapTypes[0];
					}
				} else if (methodName.equals("forEach")) {
					// forEach(BiConsumer<K, V>)
					if (paramIndex == 0) {
						return mapTypes[0];
					} else if (paramIndex == 1) {
						return mapTypes[1];
					}
				}
			}
		}

		// 默认返回Object
		return "java.lang.Object";
	}

	/**
	 * 从常见的函数式接口上下文中推断类型
	 */
	private static String inferFromCommonFunctionalContexts(LambdaExpr lambdaExpr, CompilationUnit cu, String paramName) {
		// 检查是否在某些特定的上下文中使用
		Optional<MethodCallExpr> parentCall = findAncestorMethodCall(lambdaExpr);
		if (parentCall.isPresent()) {
			MethodCallExpr call = parentCall.get();
			String methodName = call.getNameAsString();

			// 尝试处理一些常见的场景
			if (methodName.equals("ifPresent") || methodName.equals("orElseGet")) {
				// Optional的方法
				if (call.getScope().isPresent()) {
					Expression scope = call.getScope().get();
					String optionalType = inferExpressionType(scope, cu);
					return extractGenericTypeParameter(optionalType);
				}
			} else if (methodName.equals("thenApply") || methodName.equals("thenAccept") || methodName.equals("thenRun")
					|| methodName.equals("handle")) {
				// CompletableFuture的方法
				if (call.getScope().isPresent()) {
					Expression scope = call.getScope().get();
					String futureType = inferExpressionType(scope, cu);
					return extractGenericTypeParameter(futureType);
				}
			}
		}

		return "unknown";
	}

	/**
	 * 查找祖先方法调用
	 */
	private static Optional<MethodCallExpr> findAncestorMethodCall(Node node) {
		return node.findAncestor(MethodCallExpr.class);
	}

	/**
	 * 从方法引用中推断类型
	 */
	private static String inferTypeFromMethodReference(MethodReferenceExpr methodRef, CompilationUnit cu, int paramIndex) {
		if (methodRef.getScope() instanceof NameExpr) {
			String scopeName = ((NameExpr) methodRef.getScope()).getNameAsString();
			String scopeType = ParseUtil.tryGetVarType(cu, scopeName, methodRef);

			if (!scopeType.equals("unknown")) {
				// 对于实例方法引用，第一个参数通常是接收者类型
				if (methodRef.getIdentifier().equals("new")) {
					// 构造方法引用
					return scopeType;
				} else {
					// 实例方法引用
					return scopeType;
				}
			}
		} else if (methodRef.getScope() instanceof TypeExpr) {
			// 静态方法引用或构造方法引用
			return methodRef.getScope().toString();
		}

		return "unknown";
	}

	/**
	 * 处理嵌套的Lambda表达式
	 */
	private static String inferNestedLambdaParameterType(LambdaExpr outerLambda, LambdaExpr innerLambda, String paramName,
			CompilationUnit cu) {
		// 检查内部Lambda是否作为外部Lambda的一部分
		if (outerLambda.getBody().findAll(LambdaExpr.class).contains(innerLambda)) {
			// 这是一个嵌套的Lambda表达式

			// 获取外部Lambda的参数类型
			for (Parameter outerParam : outerLambda.getParameters()) {
				String outerParamName = outerParam.getNameAsString();
				String outerParamType = inferLambdaParameterType(cu, outerLambda, outerParamName, outerLambda);

				// 检查内部Lambda是否使用外部Lambda的参数
				if (innerLambda.getBody().toString().contains(outerParamName)) {
					// 内部Lambda可能与外部Lambda参数有关
					if (outerParamType.contains("Function<") || outerParamType.contains("BiFunction<")) {
						// 如果外部参数是函数类型，内部Lambda可能与其返回类型相关
						String returnType = extractFunctionReturnType(outerParamType);
						if (!returnType.equals("unknown")) {
							return returnType;
						}
					}
				}
			}
		}

		return "unknown";
	}

	/**
	 * 提取函数式接口的返回类型
	 */
	private static String extractFunctionReturnType(String functionType) {
		if (functionType.contains("Function<") || functionType.contains("BiFunction<")) {
			String[] params = extractGenericTypeParameters(functionType);
			if (params.length > 1) {
				// 对于Function<T, R>，返回类型是R（最后一个参数）
				return params[params.length - 1];
			}
		}
		return "unknown";
	}

	/**
	 * 从Lambda体内的方法调用推断参数类型
	 */
	private static String inferFromLambdaBodyMethodCalls(LambdaExpr lambdaExpr, String paramName, CompilationUnit cu) {
		// 检查Lambda体内是否有使用参数的方法调用
		if (lambdaExpr.getBody() instanceof BlockStmt) {
			BlockStmt body = (BlockStmt) lambdaExpr.getBody();

			// 查找所有方法调用
			List<MethodCallExpr> methodCalls = body.findAll(MethodCallExpr.class);
			for (MethodCallExpr call : methodCalls) {
				// 检查是否调用了参数的方法
				if (call.getScope().isPresent() && call.getScope().get() instanceof NameExpr) {
					NameExpr scope = (NameExpr) call.getScope().get();
					if (scope.getNameAsString().equals(paramName)) {
						// 参数被用作方法调用的对象
						String methodName = call.getNameAsString();

						// 基于常见方法名推断类型
						String inferredType = inferTypeFromMethodName(methodName);
						if (!inferredType.equals("unknown")) {
							return inferredType;
						}
					}
				}
			}
		} else {
			// 对于非块语句的Lambda主体，通常是单个表达式
		    Node body = lambdaExpr.getBody();
		    Expression expr = null;
		    
		    if (body instanceof ExpressionStmt) {
		        // 如果是表达式语句，获取内部表达式
		        expr = ((ExpressionStmt) body).getExpression();
		    } else if (body instanceof Expression) {
		        // 有些JavaParser版本可能直接返回Expression
		        expr = (Expression) body;
		    }

			if (expr instanceof MethodCallExpr) {
				MethodCallExpr call = (MethodCallExpr) expr;

				// 检查是否调用了参数的方法
				if (call.getScope().isPresent() && call.getScope().get() instanceof NameExpr) {
					NameExpr scope = (NameExpr) call.getScope().get();
					if (scope.getNameAsString().equals(paramName)) {
						// 参数被用作方法调用的对象
						String methodName = call.getNameAsString();

						// 基于常见方法名推断类型
						String inferredType = inferTypeFromMethodName(methodName);
						if (!inferredType.equals("unknown")) {
							return inferredType;
						}
					}
				}
			}
		}

		return "unknown";
	}

	/**
	 * 基于方法名推断对象类型
	 */
	private static String inferTypeFromMethodName(String methodName) {
		// 基于常见方法名推断类型
		if (methodName.equals("length") || methodName.equals("charAt") || methodName.equals("substring") || methodName.equals("startsWith")
				|| methodName.equals("endsWith") || methodName.equals("trim")) {
			return "java.lang.String";
		} else if (methodName.equals("size") || methodName.equals("add") || methodName.equals("remove") || methodName.equals("contains")
				|| methodName.equals("isEmpty")) {
			return "java.util.Collection";
		} else if (methodName.equals("get") || methodName.equals("set") || methodName.equals("indexOf")
				|| methodName.equals("lastIndexOf")) {
			return "java.util.List";
		} else if (methodName.equals("put") || methodName.equals("containsKey") || methodName.equals("containsValue")
				|| methodName.equals("keySet") || methodName.equals("values")) {
			return "java.util.Map";
		} else if (methodName.equals("next") || methodName.equals("hasNext")) {
			return "java.util.Iterator";
		} else if (methodName.equals("isPresent") || methodName.equals("orElse") || methodName.equals("orElseGet")) {
			return "java.util.Optional";
		}

		return "unknown";
	}

	/**
	 * 从常见的方法调用模式中推断类型
	 */
	private static String inferFromCommonMethodPatterns(MethodCallExpr call, LambdaExpr lambdaExpr, String paramName, int paramIndex,
			CompilationUnit cu) {
		String methodName = call.getNameAsString();

		// 处理Optional类的方法
		if (methodName.equals("map") || methodName.equals("flatMap") || methodName.equals("filter") || methodName.equals("ifPresent")) {
			if (call.getScope().isPresent()) {
				Expression scope = call.getScope().get();
				String scopeType = inferExpressionType(scope, cu);

				if (scopeType.contains("Optional<")) {
					// 对于Optional的方法，Lambda参数类型是Optional的包装类型
					return extractGenericTypeParameter(scopeType);
				}
			}
		}

		// 处理CompletableFuture类的方法
		if (methodName.equals("thenApply") || methodName.equals("thenAccept") || methodName.equals("thenCompose")
				|| methodName.equals("handle")) {
			if (call.getScope().isPresent()) {
				Expression scope = call.getScope().get();
				String scopeType = inferExpressionType(scope, cu);

				if (scopeType.contains("CompletableFuture<")) {
					// 对于CompletableFuture的方法，Lambda参数类型是Future的结果类型
					return extractGenericTypeParameter(scopeType);
				}
			}
		}

		// 处理其他常见的方法调用模式
		if (methodName.equals("computeIfAbsent") || methodName.equals("computeIfPresent") || methodName.equals("compute")) {
			// Map的计算方法
			if (call.getScope().isPresent()) {
				Expression scope = call.getScope().get();
				String scopeType = inferExpressionType(scope, cu);

				if (scopeType.contains("Map<")) {
					String[] params = extractGenericTypeParameters(scopeType);
					if (params.length > 1) {
						// computeIfAbsent的Lambda参数是键类型
						if (paramIndex == 0) {
							return params[0]; // 键类型
						}
					}
				}
			}
		}

		return "unknown";
	}

	/**
	 * 获取lambda参数在参数列表中的索引
	 */
	public static int getParameterIndex(LambdaExpr lambdaExpr, String paramName) {
		List<Parameter> parameters = lambdaExpr.getParameters();
		for (int i = 0; i < parameters.size(); i++) {
			if (parameters.get(i).getNameAsString().equals(paramName)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * 根据函数式接口类型和参数索引推断参数类型
	 * @param functionalInterfaceType 函数式接口类型
	 * @param paramIndex 参数索引
	 * @return 推断的参数类型
	 */
	public static String inferFunctionalInterfaceParameterType(String functionalInterfaceType, int paramIndex) {
		// 处理常见的函数式接口
		String simpleName = functionalInterfaceType;
		if (functionalInterfaceType.contains(".")) {
			simpleName = functionalInterfaceType.substring(functionalInterfaceType.lastIndexOf('.') + 1);
		}

		// 提取泛型参数
		List<String> genericParams = extractGenericParameters(functionalInterfaceType);

		// Function<T, R>: 第一个参数类型是T
		if (functionalInterfaceType.contains("Function<") || simpleName.endsWith("Function")) {
			if (paramIndex == 0 && !genericParams.isEmpty()) {
				return genericParams.get(0);
			}
		}

		// BiFunction<T, U, R>: 第一个参数类型是T，第二个是U
		if (functionalInterfaceType.contains("BiFunction<") || simpleName.equals("BiFunction")) {
			if (paramIndex == 0 && genericParams.size() > 0) {
				return genericParams.get(0);
			} else if (paramIndex == 1 && genericParams.size() > 1) {
				return genericParams.get(1);
			}
		}

		// Consumer<T>: 参数类型是T
		if (functionalInterfaceType.contains("Consumer<") || (simpleName.endsWith("Consumer") && !simpleName.startsWith("Bi"))) {
			if (paramIndex == 0 && !genericParams.isEmpty()) {
				return genericParams.get(0);
			}
		}

		// BiConsumer<T, U>: 第一个参数类型是T，第二个是U
		if (functionalInterfaceType.contains("BiConsumer<") || simpleName.equals("BiConsumer")) {
			if (paramIndex == 0 && genericParams.size() > 0) {
				return genericParams.get(0);
			} else if (paramIndex == 1 && genericParams.size() > 1) {
				return genericParams.get(1);
			}
		}

		// Predicate<T>: 参数类型是T
		if (functionalInterfaceType.contains("Predicate<") || (simpleName.endsWith("Predicate") && !simpleName.startsWith("Bi"))) {
			if (paramIndex == 0 && !genericParams.isEmpty()) {
				return genericParams.get(0);
			}
		}

		// BiPredicate<T, U>: 第一个参数类型是T，第二个是U
		if (functionalInterfaceType.contains("BiPredicate<") || simpleName.equals("BiPredicate")) {
			if (paramIndex == 0 && genericParams.size() > 0) {
				return genericParams.get(0);
			} else if (paramIndex == 1 && genericParams.size() > 1) {
				return genericParams.get(1);
			}
		}

		// UnaryOperator<T>: 参数类型是T
		if (functionalInterfaceType.contains("UnaryOperator<") || simpleName.equals("UnaryOperator")) {
			if (paramIndex == 0 && !genericParams.isEmpty()) {
				return genericParams.get(0);
			}
		}

		// BinaryOperator<T>: 两个参数类型都是T
		if (functionalInterfaceType.contains("BinaryOperator<") || simpleName.equals("BinaryOperator")) {
			if ((paramIndex == 0 || paramIndex == 1) && !genericParams.isEmpty()) {
				return genericParams.get(0);
			}
		}

		// Comparator<T>: 两个参数类型都是T
		if (functionalInterfaceType.contains("Comparator<") || simpleName.equals("Comparator")) {
			if ((paramIndex == 0 || paramIndex == 1) && !genericParams.isEmpty()) {
				return genericParams.get(0);
			}
		}

		// 处理自定义函数式接口
		if (!genericParams.isEmpty()) {
			// 如果我们有泛型参数，尝试基于命名约定推断
			if (simpleName.endsWith("Function") || simpleName.endsWith("Mapper")) {
				if (paramIndex == 0 && genericParams.size() > 0) {
					return genericParams.get(0);
				}
			} else if (simpleName.endsWith("Consumer") || simpleName.endsWith("Handler")) {
				if (paramIndex == 0 && genericParams.size() > 0) {
					return genericParams.get(0);
				}
			} else if (simpleName.endsWith("Predicate") || simpleName.endsWith("Filter")) {
				if (paramIndex == 0 && genericParams.size() > 0) {
					return genericParams.get(0);
				}
			}

			// 对于其他情况，如果参数索引在泛型参数范围内，返回对应的泛型参数
			if (paramIndex < genericParams.size()) {
				return genericParams.get(paramIndex);
			}
		}

		// 返回Object作为后备
		return "java.lang.Object";
	}

	/**
	 * 根据调用者类型、方法名和参数索引推断函数式接口参数类型
	 * @param callerType 调用者的类型
	 * @param methodName 方法名
	 * @param paramIndex Lambda表达式的参数索引
	 * @return 推断的参数类型
	 */
	public static String inferFunctionalInterfaceParameterType(String callerType, String methodName, int paramIndex) {
		// 处理Stream API的常见方法
		if (callerType.contains("Stream<") || callerType.contains("stream.Stream")) {
			String elementType = extractGenericParameterFromStream(callerType);

			if (methodName.equals("filter")) {
				// filter(Predicate<T>)
				return elementType;
			} else if (methodName.equals("map")) {
				// map(Function<T, R>)
				return elementType;
			} else if (methodName.equals("flatMap")) {
				// flatMap(Function<T, Stream<R>>)
				return elementType;
			} else if (methodName.equals("forEach") || methodName.equals("forEachOrdered")) {
				// forEach(Consumer<T>)
				return elementType;
			} else if (methodName.equals("reduce")) {
				// reduce有多个重载版本
				if (paramIndex == 0 || paramIndex == 1) {
					return elementType;
				}
			} else if (methodName.equals("collect")) {
				// collect通常使用Collector，参数类型取决于Collector
				return elementType;
			} else if (methodName.equals("anyMatch") || methodName.equals("allMatch") || methodName.equals("noneMatch")) {
				// *Match(Predicate<T>)
				return elementType;
			}
		}

		// 处理Collection的常见方法
		if (callerType.contains("Collection<") || callerType.contains("List<") || callerType.contains("Set<")
				|| callerType.contains("Queue<")) {
			String elementType = extractGenericParameterFromCollection(callerType);

			if (methodName.equals("forEach")) {
				// forEach(Consumer<T>)
				return elementType;
			} else if (methodName.equals("removeIf")) {
				// removeIf(Predicate<T>)
				return elementType;
			} else if (methodName.equals("replaceAll")) {
				// List.replaceAll(UnaryOperator<E>)
				return elementType;
			} else if (methodName.equals("sort")) {
				// List.sort(Comparator<E>)
				return elementType;
			}
		}

		// 处理Map的常见方法
		if (callerType.contains("Map<")) {
			List<String> mapGenericParams = extractGenericParameters(callerType);
			String keyType = mapGenericParams.size() > 0 ? mapGenericParams.get(0) : "java.lang.Object";
			String valueType = mapGenericParams.size() > 1 ? mapGenericParams.get(1) : "java.lang.Object";

			if (methodName.equals("forEach")) {
				// forEach(BiConsumer<K, V>)
				if (paramIndex == 0) {
					return keyType;
				} else if (paramIndex == 1) {
					return valueType;
				}
			} else if (methodName.equals("compute") || methodName.equals("computeIfPresent")) {
				// compute(K, BiFunction<K, V, V>)
				if (paramIndex == 0) {
					return keyType;
				} else if (paramIndex == 1) {
					return valueType;
				}
			} else if (methodName.equals("computeIfAbsent")) {
				// computeIfAbsent(K, Function<K, V>)
				return keyType;
			} else if (methodName.equals("merge")) {
				// merge(K, V, BiFunction<V, V, V>)
				return valueType;
			} else if (methodName.equals("replaceAll")) {
				// replaceAll(BiFunction<K, V, V>)
				if (paramIndex == 0) {
					return keyType;
				} else if (paramIndex == 1) {
					return valueType;
				}
			}
		}

		// 处理Optional的方法
		if (callerType.contains("Optional<")) {
			String wrappedType = extractGenericParameters(callerType).get(0);

			if (methodName.equals("filter")) {
				// filter(Predicate<T>)
				return wrappedType;
			} else if (methodName.equals("map")) {
				// map(Function<T, U>)
				return wrappedType;
			} else if (methodName.equals("flatMap")) {
				// flatMap(Function<T, Optional<U>>)
				return wrappedType;
			} else if (methodName.equals("ifPresent")) {
				// ifPresent(Consumer<T>)
				return wrappedType;
			} else if (methodName.equals("orElseGet")) {
				// orElseGet(Supplier<T>)
				// Supplier无参数，所以不返回类型
				return "java.lang.Object";
			}
		}

		// 处理CompletableFuture的方法
		if (callerType.contains("CompletableFuture<")) {
			String futureType = extractGenericParameters(callerType).get(0);

			if (methodName.equals("thenApply") || methodName.equals("thenCompose")) {
				// thenApply(Function<T, U>)
				return futureType;
			} else if (methodName.equals("thenAccept")) {
				// thenAccept(Consumer<T>)
				return futureType;
			} else if (methodName.equals("handle")) {
				// handle(BiFunction<T, Throwable, U>)
				if (paramIndex == 0) {
					return futureType;
				} else if (paramIndex == 1) {
					return "java.lang.Throwable";
				}
			} else if (methodName.equals("exceptionally")) {
				// exceptionally(Function<Throwable, T>)
				return "java.lang.Throwable";
			}
		}

		// 处理其他常见方法
		if (methodName.equals("compareTo") || methodName.equals("equals")) {
			if (paramIndex == 0) {
				return callerType;
			}
		}

		// 处理自定义方法和其他情况
		if (methodName.endsWith("ForEach") || methodName.startsWith("forEach")) {
			// 通常是Consumer类型的参数
			return extractElementTypeFromCaller(callerType);
		} else if (methodName.endsWith("Filter") || methodName.startsWith("filter")) {
			// 通常是Predicate类型的参数
			return extractElementTypeFromCaller(callerType);
		} else if (methodName.endsWith("Map") || methodName.startsWith("map") || methodName.endsWith("Transform")
				|| methodName.startsWith("transform")) {
			// 通常是Function类型的参数
			return extractElementTypeFromCaller(callerType);
		} else if (methodName.endsWith("Sort") || methodName.startsWith("sort")) {
			// 通常是Comparator类型的参数
			return extractElementTypeFromCaller(callerType);
		}

		// 返回Object作为后备
		return "java.lang.Object";
	}

	/**
	 * 根据调用者类型、方法名、参数位置和参数索引推断函数式接口参数类型
	 * @param callerType 调用者的类型
	 * @param methodName 方法名
	 * @param argPosition Lambda表达式在方法调用中的参数位置
	 * @param paramIndex Lambda表达式的参数索引
	 * @param cu 编译单元，用于额外的上下文信息
	 * @return 推断的参数类型
	 */
	public static String inferFunctionalInterfaceParameterType(String callerType, String methodName, int argPosition, int paramIndex,
			CompilationUnit cu) {
		// 处理Stream的特殊方法，这些方法的行为取决于Lambda在方法调用中的位置
		if (callerType.contains("Stream<")) {
			String elementType = extractGenericParameterFromStream(callerType);

			if (methodName.equals("reduce")) {
				// reduce有多个重载:
				// reduce(T identity, BinaryOperator<T>)
				// reduce(BinaryOperator<T>)
				if (argPosition == 0) {
					// 只有一个参数时，是BinaryOperator<T>
					return elementType;
				} else if (argPosition == 1) {
					// 两个参数时，第二个是BinaryOperator<T>
					return elementType;
				}
			} else if (methodName.equals("collect")) {
				// collect(Collector<T, A, R>)
				// 处理复杂，简化实现
				return elementType;
			}
		}

		// 对于其他方法，可以重用前面的实现
		return inferFunctionalInterfaceParameterType(callerType, methodName, paramIndex);
	}

	/**
	 * 从Stream类型中提取元素类型
	 */
	private static String extractGenericParameterFromStream(String streamType) {
		List<String> params = extractGenericParameters(streamType);
		if (!params.isEmpty()) {
			return params.get(0);
		}
		return "java.lang.Object";
	}

	/**
	 * 从Collection类型中提取元素类型
	 */
	private static String extractGenericParameterFromCollection(String collectionType) {
		List<String> params = extractGenericParameters(collectionType);
		if (!params.isEmpty()) {
			return params.get(0);
		}
		return "java.lang.Object";
	}

	/**
	 * 从泛型类型中提取所有泛型参数
	 * @param genericType 泛型类型字符串
	 * @return 泛型参数列表
	 */
	private static List<String> extractGenericParameters(String genericType) {
		List<String> results = new ArrayList<>();

		if (!genericType.contains("<") || !genericType.contains(">")) {
			return results;
		}

		int start = genericType.indexOf('<');
		int end = genericType.lastIndexOf('>');

		if (start >= end) {
			return results;
		}

		String paramsStr = genericType.substring(start + 1, end).trim();

		// 处理嵌套泛型
		int depth = 0;
		StringBuilder currentParam = new StringBuilder();

		for (int i = 0; i < paramsStr.length(); i++) {
			char c = paramsStr.charAt(i);

			if (c == '<') {
				depth++;
				currentParam.append(c);
			} else if (c == '>') {
				depth--;
				currentParam.append(c);
			} else if (c == ',' && depth == 0) {
				results.add(currentParam.toString().trim());
				currentParam = new StringBuilder();
			} else {
				currentParam.append(c);
			}
		}

		// 添加最后一个参数
		if (currentParam.length() > 0) {
			results.add(currentParam.toString().trim());
		}

		return results;
	}

	/**
	 * 从集合元素类型中提取元素类型
	 */
	public static String inferCollectionElementType(String collectionType) {
		if (collectionType == null || collectionType.equals("unknown")) {
			return "java.lang.Object";
		}

		// 处理数组类型
		if (collectionType.endsWith("[]")) {
			return collectionType.substring(0, collectionType.length() - 2);
		}

		// 处理集合类型
		if (collectionType.contains("<") && collectionType.contains(">")) {
			List<String> params = extractGenericParameters(collectionType);
			if (!params.isEmpty()) {
				return params.get(0);
			}
		}

		// 对于Map类型，通常使用value类型
		if (collectionType.contains("Map<")) {
			List<String> params = extractGenericParameters(collectionType);
			if (params.size() >= 2) {
				return params.get(1); // 返回值类型
			}
		}

		// 对于没有明确泛型的集合，或者原始类型
		if (collectionType.contains("List") || collectionType.contains("Set") || collectionType.contains("Collection")
				|| collectionType.contains("Iterable") || collectionType.contains("Queue") || collectionType.contains("Deque")) {
			return "java.lang.Object";
		}

		return "java.lang.Object";
	}

	/**
	 * 从调用者类型中提取可能的元素类型
	 */
	private static String extractElementTypeFromCaller(String callerType) {
		if (callerType.contains("List<") || callerType.contains("Set<") || callerType.contains("Collection<")
				|| callerType.contains("Iterable<") || callerType.contains("Queue<") || callerType.contains("Deque<")) {
			return extractGenericParameterFromCollection(callerType);
		} else if (callerType.contains("Map<")) {
			// 对于Map，默认返回值类型
			List<String> params = extractGenericParameters(callerType);
			if (params.size() >= 2) {
				return params.get(1); // 值类型
			}
		} else if (callerType.contains("Stream<")) {
			return extractGenericParameterFromStream(callerType);
		} else if (callerType.contains("Optional<")) {
			List<String> params = extractGenericParameters(callerType);
			if (!params.isEmpty()) {
				return params.get(0);
			}
		} else if (callerType.contains("CompletableFuture<") || callerType.contains("Future<")) {
			List<String> params = extractGenericParameters(callerType);
			if (!params.isEmpty()) {
				return params.get(0);
			}
		}

		// 如果是数组类型
		if (callerType.endsWith("[]")) {
			return callerType.substring(0, callerType.length() - 2);
		}

		return "java.lang.Object";
	}

}
