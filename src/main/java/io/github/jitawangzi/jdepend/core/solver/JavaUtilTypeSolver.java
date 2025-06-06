package io.github.jitawangzi.jdepend.core.solver;

import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.model.SymbolReference;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.reflectionmodel.ReflectionClassDeclaration;
import com.github.javaparser.symbolsolver.reflectionmodel.ReflectionInterfaceDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**

java.util包专用类型解析器
*/
public class JavaUtilTypeSolver implements TypeSolver {
private static final Logger log = LoggerFactory.getLogger(JavaUtilTypeSolver.class);
private final Map<String, ResolvedReferenceTypeDeclaration> utilTypes = new HashMap<>();

public JavaUtilTypeSolver() {
log.info("初始化java.util类型解析器");
preloadUtilTypes();
}

private void preloadUtilTypes() {
try {
// 集合接口
preloadInterface("java.util.Collection");
preloadInterface("java.util.List");
preloadInterface("java.util.Set");
preloadInterface("java.util.Map");
preloadInterface("java.util.Queue");
preloadInterface("java.util.Deque");
preloadInterface("java.util.SortedSet");
preloadInterface("java.util.SortedMap");
preloadInterface("java.util.NavigableSet");
preloadInterface("java.util.NavigableMap");
preloadInterface("java.util.Iterator");
preloadInterface("java.util.ListIterator");


     // 集合实现类
     preloadClass("java.util.ArrayList");
     preloadClass("java.util.LinkedList");
     preloadClass("java.util.HashSet");
     preloadClass("java.util.LinkedHashSet");
     preloadClass("java.util.TreeSet");
     preloadClass("java.util.HashMap");
     preloadClass("java.util.LinkedHashMap");
     preloadClass("java.util.TreeMap");
     preloadClass("java.util.PriorityQueue");
     preloadClass("java.util.ArrayDeque");
     preloadClass("java.util.Vector");
     preloadClass("java.util.Stack");
     preloadClass("java.util.Hashtable");
     preloadClass("java.util.Properties");
     
     // 工具类
     preloadClass("java.util.Arrays");
     preloadClass("java.util.Collections");
     preloadClass("java.util.Objects");
     preloadClass("java.util.StringJoiner");
     preloadClass("java.util.Scanner");
     preloadClass("java.util.Random");
     preloadClass("java.util.Locale");
     preloadClass("java.util.UUID");
     preloadClass("java.util.Base64");
     
     // 日期相关
     preloadClass("java.util.Date");
     preloadClass("java.util.Calendar");
     preloadClass("java.util.TimeZone");
     
     log.info("成功加载java.util包类型声明");
 } catch (Exception e) {
     log.error("预加载java.util类型失败: {}", e.getMessage());
 }
}

private void preloadClass(String className) {
try {
Class<?> clazz = Class.forName(className);
ReflectionClassDeclaration declaration = new ReflectionClassDeclaration(clazz, this);
String simpleName = className.substring(className.lastIndexOf('.') + 1);
utilTypes.put(className, declaration);
utilTypes.put(simpleName, declaration);
} catch (ClassNotFoundException e) {
log.warn("类型未找到: {}", className);
} catch (Exception e) {
log.warn("加载类型失败: {}: {}", className, e.getMessage());
}
}

private void preloadInterface(String interfaceName) {
try {
Class<?> interfaceClass = Class.forName(interfaceName);
ReflectionInterfaceDeclaration declaration = new ReflectionInterfaceDeclaration(interfaceClass, this);
String simpleName = interfaceName.substring(interfaceName.lastIndexOf('.') + 1);
utilTypes.put(interfaceName, declaration);
utilTypes.put(simpleName, declaration);
} catch (ClassNotFoundException e) {
log.warn("接口未找到: {}", interfaceName);
} catch (Exception e) {
log.warn("加载接口失败: {}: {}", interfaceName, e.getMessage());
}
}

@Override
public TypeSolver getParent() {
return null; // 不使用父解析器
}

@Override
public void setParent(TypeSolver parent) {
// 不使用父解析器
}

@Override
public SymbolReference<ResolvedReferenceTypeDeclaration> tryToSolveType(String name) {
if (utilTypes.containsKey(name)) {
return SymbolReference.solved(utilTypes.get(name));
}


 // 尝试加载未预加载的java.util类型
 if (name.startsWith("java.util.")) {
     try {
         Class<?> clazz = Class.forName(name);
         if (clazz.isInterface()) {
             ReflectionInterfaceDeclaration declaration = new ReflectionInterfaceDeclaration(clazz, this);
             utilTypes.put(name, declaration);
             String simpleName = name.substring(name.lastIndexOf('.') + 1);
             utilTypes.put(simpleName, declaration);
             return SymbolReference.solved(declaration);
         } else {
             ReflectionClassDeclaration declaration = new ReflectionClassDeclaration(clazz, this);
             utilTypes.put(name, declaration);
             String simpleName = name.substring(name.lastIndexOf('.') + 1);
             utilTypes.put(simpleName, declaration);
             return SymbolReference.solved(declaration);
         }
     } catch (ClassNotFoundException e) {
         // 类未找到，返回unsolved
     } catch (Exception e) {
         log.warn("解析类型失败: {}: {}", name, e.getMessage());
     }
 }
 
 return SymbolReference.unsolved(ResolvedReferenceTypeDeclaration.class);
}
}

