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

java.io包专用类型解析器
*/
public class JavaIOTypeSolver implements TypeSolver {
private static final Logger log = LoggerFactory.getLogger(JavaIOTypeSolver.class);
private final Map<String, ResolvedReferenceTypeDeclaration> ioTypes = new HashMap<>();

public JavaIOTypeSolver() {
log.info("初始化java.io类型解析器");
preloadIOTypes();
}

private void preloadIOTypes() {
try {
// IO接口
preloadInterface("java.io.Closeable");
preloadInterface("java.io.Flushable");
preloadInterface("java.io.DataInput");
preloadInterface("java.io.DataOutput");
preloadInterface("java.io.ObjectInput");
preloadInterface("java.io.ObjectOutput");
preloadInterface("java.io.Serializable");
preloadInterface("java.io.Externalizable");


     // IO类
     preloadClass("java.io.File");
     preloadClass("java.io.InputStream");
     preloadClass("java.io.OutputStream");
     preloadClass("java.io.FileInputStream");
     preloadClass("java.io.FileOutputStream");
     preloadClass("java.io.ByteArrayInputStream");
     preloadClass("java.io.ByteArrayOutputStream");
     preloadClass("java.io.FilterInputStream");
     preloadClass("java.io.FilterOutputStream");
     preloadClass("java.io.BufferedInputStream");
     preloadClass("java.io.BufferedOutputStream");
     preloadClass("java.io.DataInputStream");
     preloadClass("java.io.DataOutputStream");
     preloadClass("java.io.ObjectInputStream");
     preloadClass("java.io.ObjectOutputStream");
     preloadClass("java.io.Reader");
     preloadClass("java.io.Writer");
     preloadClass("java.io.InputStreamReader");
     preloadClass("java.io.OutputStreamWriter");
     preloadClass("java.io.FileReader");
     preloadClass("java.io.FileWriter");
     preloadClass("java.io.BufferedReader");
     preloadClass("java.io.BufferedWriter");
     preloadClass("java.io.PrintStream");
     preloadClass("java.io.PrintWriter");
     preloadClass("java.io.StringReader");
     preloadClass("java.io.StringWriter");
     
     // 异常类
     preloadClass("java.io.IOException");
     preloadClass("java.io.FileNotFoundException");
     preloadClass("java.io.EOFException");
     
     log.info("成功加载java.io包类型声明");
 } catch (Exception e) {
     log.error("预加载java.io类型失败: {}", e.getMessage());
 }
}

private void preloadClass(String className) {
try {
Class<?> clazz = Class.forName(className);
ReflectionClassDeclaration declaration = new ReflectionClassDeclaration(clazz, this);
String simpleName = className.substring(className.lastIndexOf('.') + 1);
ioTypes.put(className, declaration);
ioTypes.put(simpleName, declaration);
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
ioTypes.put(interfaceName, declaration);
ioTypes.put(simpleName, declaration);
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
if (ioTypes.containsKey(name)) {
return SymbolReference.solved(ioTypes.get(name));
}


 // 尝试加载未预加载的java.io类型
 if (name.startsWith("java.io.")) {
     try {
         Class<?> clazz = Class.forName(name);
         if (clazz.isInterface()) {
             ReflectionInterfaceDeclaration declaration = new ReflectionInterfaceDeclaration(clazz, this);
             ioTypes.put(name, declaration);
             String simpleName = name.substring(name.lastIndexOf('.') + 1);
             ioTypes.put(simpleName, declaration);
             return SymbolReference.solved(declaration);
         } else {
             ReflectionClassDeclaration declaration = new ReflectionClassDeclaration(clazz, this);
             ioTypes.put(name, declaration);
             String simpleName = name.substring(name.lastIndexOf('.') + 1);
             ioTypes.put(simpleName, declaration);
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

