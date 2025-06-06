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

java.net包专用类型解析器
*/
public class JavaNetTypeSolver implements TypeSolver {
private static final Logger log = LoggerFactory.getLogger(JavaNetTypeSolver.class);
private final Map<String, ResolvedReferenceTypeDeclaration> netTypes = new HashMap<>();

public JavaNetTypeSolver() {
log.info("初始化java.net类型解析器");
preloadNetTypes();
}

private void preloadNetTypes() {
try {
// 网络通信类
preloadClass("java.net.Socket");
preloadClass("java.net.ServerSocket");
preloadClass("java.net.DatagramSocket");
preloadClass("java.net.DatagramPacket");
preloadClass("java.net.MulticastSocket");


     // URL相关
     preloadClass("java.net.URL");
     preloadClass("java.net.URI");
     preloadClass("java.net.URLConnection");
     preloadClass("java.net.HttpURLConnection");
     preloadClass("java.net.URLEncoder");
     preloadClass("java.net.URLDecoder");
     
     // 地址相关
     preloadClass("java.net.InetAddress");
     preloadClass("java.net.Inet4Address");
     preloadClass("java.net.Inet6Address");
     preloadClass("java.net.NetworkInterface");
     
     // 代理相关
     preloadClass("java.net.Proxy");
     preloadClass("java.net.ProxySelector");
     
     // 接口
     preloadInterface("java.net.SocketOption");
     preloadInterface("java.net.SocketAddress");
     
     // 异常类
     preloadClass("java.net.SocketException");
     preloadClass("java.net.UnknownHostException");
     preloadClass("java.net.MalformedURLException");
     preloadClass("java.net.ProtocolException");
     
     log.info("成功加载java.net包类型声明");
 } catch (Exception e) {
     log.error("预加载java.net类型失败: {}", e.getMessage());
 }
}

private void preloadClass(String className) {
try {
Class<?> clazz = Class.forName(className);
ReflectionClassDeclaration declaration = new ReflectionClassDeclaration(clazz, this);
String simpleName = className.substring(className.lastIndexOf('.') + 1);
netTypes.put(className, declaration);
netTypes.put(simpleName, declaration);
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
netTypes.put(interfaceName, declaration);
netTypes.put(simpleName, declaration);
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
if (netTypes.containsKey(name)) {
return SymbolReference.solved(netTypes.get(name));
}


 // 尝试加载未预加载的java.net类型
 if (name.startsWith("java.net.")) {
     try {
         Class<?> clazz = Class.forName(name);
         if (clazz.isInterface()) {
             ReflectionInterfaceDeclaration declaration = new ReflectionInterfaceDeclaration(clazz, this);
             netTypes.put(name, declaration);
             String simpleName = name.substring(name.lastIndexOf('.') + 1);
             netTypes.put(simpleName, declaration);
             return SymbolReference.solved(declaration);
         } else {
             ReflectionClassDeclaration declaration = new ReflectionClassDeclaration(clazz, this);
             netTypes.put(name, declaration);
             String simpleName = name.substring(name.lastIndexOf('.') + 1);
             netTypes.put(simpleName, declaration);
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

