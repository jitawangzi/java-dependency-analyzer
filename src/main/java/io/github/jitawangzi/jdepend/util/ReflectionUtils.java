package io.github.jitawangzi.jdepend.util;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.LinkedHashSet;
import java.util.Set;

@Deprecated
public class ReflectionUtils {

	/**
     * 在类层次结构中查找方法（包含父类和接口）
     */
	public static Method findMethod(Class<?> clazz, String methodName) throws NoSuchMethodException {
        
        // 当前类查找
        try {
        	Method[] methods = clazz.getMethods(); 
        	for (Method method : methods) {
        			if (method.getName().equals(methodName)) {
            			return method;
            		}
            	}
			} catch (Exception ignored) {
			}
        
        // 父类查找
        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null && superClass != Object.class) {
            try {
				return findMethod(superClass, methodName);
            } catch (NoSuchMethodException ignored) {}
        }
        
        // 接口查找（含多层接口）
        for (Class<?> iface : getAllInterfaces(clazz)) {
            try {
				return findMethod(iface, methodName);
            } catch (NoSuchMethodException ignored) {}
        }
        
		throw new NoSuchMethodException(
				"Method " + methodName + " not found in class " + clazz.getName() + " or its superclasses/interfaces.");
    }

	/**
	 * 获取类实现的所有接口（包括父接口）
	 */
	private static Set<Class<?>> getAllInterfaces(Class<?> clazz) {
		Set<Class<?>> interfaces = new LinkedHashSet<>();
		Class<?> current = clazz;

		while (current != null && current != Object.class) {
			for (Class<?> iface : current.getInterfaces()) {
				interfaces.add(iface);
				interfaces.addAll(getAllInterfaces(iface)); // 递归获取父接口
			}
			current = current.getSuperclass();
		}
		return interfaces;
	}

	/**
	 * 获取方法返回类型,类全限定名
	 * @throws ClassNotFoundException 
	 */
	public static String getMethodReturnType(String fullClassName, String methodName) {
		try {
			// 如果类名带有泛型，则暂时去掉
			if (fullClassName.contains("<")) {
				fullClassName = fullClassName.substring(0, fullClassName.indexOf('<'));
			}
			Class<?> clazz = Class.forName(fullClassName);
			return findMethod(clazz, methodName).getGenericReturnType().getTypeName();
		} catch (Exception e) {
			throw new RuntimeException("getMethodReturnType fail , " + fullClassName + " " + methodName, e);
		}
	}

	public static String getMethodReturnTypeWithGeneric(String fullClassName, String methodName) {
	    try {
	        Class<?> clazz = Class.forName(fullClassName.replaceAll("<.*>", ""));
	        Method method = findMethod(clazz, methodName);
	        Type returnType = method.getGenericReturnType();

	        // 处理泛型类型
	        if (returnType instanceof ParameterizedType) {
	            ParameterizedType pt = (ParameterizedType) returnType;
	            Type rawType = pt.getRawType();
	            Type[] typeArgs = pt.getActualTypeArguments();

	            // 构建带具体泛型的类型名（如 List<String>）
	            StringBuilder sb = new StringBuilder(rawType.getTypeName());
	            sb.append('<');
	            for (int i = 0; i < typeArgs.length; i++) {
	                if (i > 0) sb.append(',');
	                sb.append(typeArgs[i].getTypeName()); // 获取泛型参数类型
	            }
	            sb.append('>');
	            return sb.toString();
	        }
	        return returnType.getTypeName(); // 非泛型直接返回
	    } catch (Exception e) {
	        throw new RuntimeException("Failed to get method return type: " + fullClassName + "#" + methodName, e);
	    }
	}

}