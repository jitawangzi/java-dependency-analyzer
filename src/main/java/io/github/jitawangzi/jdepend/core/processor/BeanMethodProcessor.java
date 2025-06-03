package io.github.jitawangzi.jdepend.core.processor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;

import io.github.jitawangzi.jdepend.config.AppConfig;

/**
 * JavaBean方法处理器，用于识别和处理JavaBean的getter和setter方法
 */
public class BeanMethodProcessor {

	// 存储每个类中被省略的访问器方法
	private final Map<String, List<MethodDeclaration>> omittedAccessors = new HashMap<>();

	/**
	 * 构造函数
	 * 
	 * @param config 配置对象
	 */
	public BeanMethodProcessor() {
	}

	/**
	 * 处理编译单元中的JavaBean方法
	 * 
	 * @param cu 编译单元
	 * @param className 类名
	 */
	public void process(CompilationUnit cu, String className) {
		if (!AppConfig.INSTANCE.isOmitBeanMethods()) {
			return;
		}

		// 收集类中的所有字段
		Map<String, String> fieldTypes = new HashMap<>();
		cu.findAll(FieldDeclaration.class).forEach(field -> {
			field.getVariables().forEach(var -> {
				fieldTypes.put(var.getNameAsString(), var.getType().asString());
			});
		});

		// 处理类中的方法
		cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
			List<MethodDeclaration> accessorsToRemove = new ArrayList<>();

			for (MethodDeclaration method : classDecl.getMethods()) {
				if (isBeanAccessor(method, fieldTypes)) {
					accessorsToRemove.add(method.clone());
				}
			}

			// 保存被省略的访问器方法
			if (!accessorsToRemove.isEmpty()) {
				omittedAccessors.put(className, accessorsToRemove);

				// 从类中移除这些方法
				for (MethodDeclaration method : accessorsToRemove) {
					classDecl.getMembers().remove(method);
				}
			}
		});
	}

	/**
	 * 判断方法是否是Bean访问器方法
	 * 
	 * @param method 方法声明
	 * @param fieldTypes 字段类型映射
	 * @return 是否是Bean访问器方法
	 */
	private boolean isBeanAccessor(MethodDeclaration method, Map<String, String> fieldTypes) {
		String methodName = method.getNameAsString();

		// 处理getter方法
		if (methodName.startsWith("get") && methodName.length() > 3) {
			return isGetter(method, fieldTypes, methodName);
		}

		// 处理boolean类型的is方法
		if (methodName.startsWith("is") && methodName.length() > 2) {
			return isGetter(method, fieldTypes, methodName);
		}

		// 处理setter方法
		if (methodName.startsWith("set") && methodName.length() > 3) {
			return isSetter(method, fieldTypes, methodName);
		}

		return false;
	}

	/**
	 * 判断方法是否是标准的getter方法
	 */
	private boolean isGetter(MethodDeclaration method, Map<String, String> fieldTypes, String methodName) {
		// getter方法不应该有参数
		if (!method.getParameters().isEmpty()) {
			return false;
		}

		// getter方法应该有返回值
		if (method.getType().isVoidType()) {
			return false;
		}

		// 提取可能的字段名
		String possibleFieldName = extractFieldName(methodName);
		if (possibleFieldName.isEmpty() || !fieldTypes.containsKey(possibleFieldName)) {
			return false;
		}

		// 检查返回值类型是否与字段类型匹配
		String fieldType = fieldTypes.get(possibleFieldName);
		if (!method.getType().asString().equals(fieldType)) {
			return false;
		}

		// 检查方法体是否是标准getter实现
		if (method.getBody().isPresent()) {
			BlockStmt body = method.getBody().get();
			if (body.getStatements().size() != 1) {
				return false;
			}

			Statement stmt = body.getStatement(0);
			if (!stmt.isReturnStmt()) {
				return false;
			}

			ReturnStmt returnStmt = stmt.asReturnStmt();
			if (!returnStmt.getExpression().isPresent()) {
				return false;
			}

			Expression expr = returnStmt.getExpression().get();

			// 检查返回的是字段还是this.字段
			return expr.isNameExpr() && expr.asNameExpr().getNameAsString().equals(possibleFieldName)
					|| (expr.isFieldAccessExpr() && expr.asFieldAccessExpr().getName().asString().equals(possibleFieldName)
							&& expr.asFieldAccessExpr().getScope().isThisExpr());
		}

		return false;
	}

	/**
	 * 判断方法是否是标准的setter方法
	 */
	private boolean isSetter(MethodDeclaration method, Map<String, String> fieldTypes, String methodName) {
		// setter方法应该只有一个参数
		NodeList<Parameter> parameters = method.getParameters();
		if (parameters.size() != 1) {
			return false;
		}

		// setter方法通常返回void
		if (!method.getType().isVoidType() && !method.getType().asString().equals("Builder")) {
			return false;
		}

		// 提取可能的字段名
		String possibleFieldName = extractFieldName(methodName);
		if (possibleFieldName.isEmpty() || !fieldTypes.containsKey(possibleFieldName)) {
			return false;
		}

		// 检查参数类型是否与字段类型匹配
		String fieldType = fieldTypes.get(possibleFieldName);
		String paramType = parameters.get(0).getType().asString();
		if (!paramType.equals(fieldType)) {
			return false;
		}

		// 检查方法体是否是标准setter实现
		if (method.getBody().isPresent()) {
			BlockStmt body = method.getBody().get();

			// 典型的setter方法应该只有一个语句
			if (body.getStatements().size() != 1) {
				return false;
			}

			Statement stmt = body.getStatement(0);
			if (!stmt.isExpressionStmt()) {
				return false;
			}

			Expression expr = stmt.asExpressionStmt().getExpression();
			if (!expr.isAssignExpr()) {
				return false;
			}

			AssignExpr assignExpr = expr.asAssignExpr();
			Expression target = assignExpr.getTarget();
			Expression value = assignExpr.getValue();

			// 检查赋值表达式是否符合 this.field = param 或 field = param 模式
			boolean isThisFieldAssignment = target.isFieldAccessExpr() && target.asFieldAccessExpr().getScope().isThisExpr()
					&& target.asFieldAccessExpr().getName().asString().equals(possibleFieldName);

			boolean isFieldAssignment = target.isNameExpr() && target.asNameExpr().getNameAsString().equals(possibleFieldName);

			boolean isParamValue = value.isNameExpr() && value.asNameExpr().getNameAsString().equals(parameters.get(0).getNameAsString());

			return (isThisFieldAssignment || isFieldAssignment) && isParamValue;
		}

		return false;
	}

	/**
	 * 从方法名中提取字段名
	 * 
	 * @param methodName 方法名
	 * @return 推断的字段名
	 */
	private String extractFieldName(String methodName) {
		String prefix;
		if (methodName.startsWith("get") || methodName.startsWith("set")) {
			prefix = methodName.substring(3);
		} else if (methodName.startsWith("is")) {
			prefix = methodName.substring(2);
		} else {
			return "";
		}

		if (prefix.isEmpty()) {
			return "";
		}

		// 将首字母转为小写
		return Character.toLowerCase(prefix.charAt(0)) + (prefix.length() > 1 ? prefix.substring(1) : "");
	}

	/**
	 * 获取被省略的访问器方法
	 * 
	 * @return 被省略的访问器方法
	 */
	public Map<String, List<MethodDeclaration>> getOmittedAccessors() {
		return omittedAccessors;
	}
}