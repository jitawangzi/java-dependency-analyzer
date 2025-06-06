package io.github.jitawangzi.jdepend.core.solver;

import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.model.SymbolReference;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.reflectionmodel.ReflectionInterfaceDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**

极简XML类型解析器 - 不依赖父解析器，只处理特定接口
*/
public class SimpleXmlTypeSolver implements TypeSolver {
private static final Logger log = LoggerFactory.getLogger(SimpleXmlTypeSolver.class);
private final Map<String, ResolvedReferenceTypeDeclaration> xmlInterfaces = new HashMap<>();

public SimpleXmlTypeSolver() {
log.info("初始化简单XML类型解析器");
// 预加载几个关键接口
preloadXmlInterfaces();
}

private void preloadXmlInterfaces() {
try {
// 处理Element接口
Class<?> elementClass = Class.forName("org.w3c.dom.Element");
ReflectionInterfaceDeclaration elementDecl = new ReflectionInterfaceDeclaration(elementClass, this);
xmlInterfaces.put("Element", elementDecl);
xmlInterfaces.put("org.w3c.dom.Element", elementDecl);

     // 处理Document接口
     Class<?> documentClass = Class.forName("org.w3c.dom.Document");
     ReflectionInterfaceDeclaration documentDecl = new ReflectionInterfaceDeclaration(documentClass, this);
     xmlInterfaces.put("Document", documentDecl);
     xmlInterfaces.put("org.w3c.dom.Document", documentDecl);
     
     // 处理Node接口
     Class<?> nodeClass = Class.forName("org.w3c.dom.Node");
     ReflectionInterfaceDeclaration nodeDecl = new ReflectionInterfaceDeclaration(nodeClass, this);
     xmlInterfaces.put("Node", nodeDecl);
     xmlInterfaces.put("org.w3c.dom.Node", nodeDecl);
     
     // 处理NodeList接口
     Class<?> nodeListClass = Class.forName("org.w3c.dom.NodeList");
     ReflectionInterfaceDeclaration nodeListDecl = new ReflectionInterfaceDeclaration(nodeListClass, this);
     xmlInterfaces.put("NodeList", nodeListDecl);
     xmlInterfaces.put("org.w3c.dom.NodeList", nodeListDecl);
     
     // 处理NamedNodeMap接口
     Class<?> namedNodeMapClass = Class.forName("org.w3c.dom.NamedNodeMap");
     ReflectionInterfaceDeclaration namedNodeMapDecl = new ReflectionInterfaceDeclaration(namedNodeMapClass, this);
     xmlInterfaces.put("NamedNodeMap", namedNodeMapDecl);
     xmlInterfaces.put("org.w3c.dom.NamedNodeMap", namedNodeMapDecl);
     
     // 处理Attr接口
     Class<?> attrClass = Class.forName("org.w3c.dom.Attr");
     ReflectionInterfaceDeclaration attrDecl = new ReflectionInterfaceDeclaration(attrClass, this);
     xmlInterfaces.put("Attr", attrDecl);
     xmlInterfaces.put("org.w3c.dom.Attr", attrDecl);
     
     // XML解析相关类
     Class<?> docBuilderClass = Class.forName("javax.xml.parsers.DocumentBuilder");
     ReflectionInterfaceDeclaration docBuilderDecl = new ReflectionInterfaceDeclaration(docBuilderClass, this);
     xmlInterfaces.put("DocumentBuilder", docBuilderDecl);
     xmlInterfaces.put("javax.xml.parsers.DocumentBuilder", docBuilderDecl);
     
     Class<?> docBuilderFactoryClass = Class.forName("javax.xml.parsers.DocumentBuilderFactory");
     ReflectionInterfaceDeclaration docBuilderFactoryDecl = new ReflectionInterfaceDeclaration(docBuilderFactoryClass, this);
     xmlInterfaces.put("DocumentBuilderFactory", docBuilderFactoryDecl);
     xmlInterfaces.put("javax.xml.parsers.DocumentBuilderFactory", docBuilderFactoryDecl);
     
     log.info("成功加载XML DOM接口声明");
 } catch (Exception e) {
     log.error("预加载XML接口失败: {}", e.getMessage());
 }
}

@Override
public TypeSolver getParent() {
return null; // 不使用父解析器
}

@Override
public void setParent(TypeSolver parent) {
// 不使用父解析器，什么都不做
}

@Override
public SymbolReference<ResolvedReferenceTypeDeclaration> tryToSolveType(String name) {
// 只处理我们预加载的XML接口
if (xmlInterfaces.containsKey(name)) {
return SymbolReference.solved(xmlInterfaces.get(name));
}


 // 对于其他类型，直接返回unsolved，不调用父解析器
 return SymbolReference.unsolved(ResolvedReferenceTypeDeclaration.class);
}
}

