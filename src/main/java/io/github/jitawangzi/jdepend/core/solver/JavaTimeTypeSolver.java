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

java.time包专用类型解析器
*/
public class JavaTimeTypeSolver implements TypeSolver {
private static final Logger log = LoggerFactory.getLogger(JavaTimeTypeSolver.class);
private final Map<String, ResolvedReferenceTypeDeclaration> timeTypes = new HashMap<>();

public JavaTimeTypeSolver() {
log.info("初始化java.time类型解析器");
preloadTimeTypes();
}

private void preloadTimeTypes() {
try {
// 核心时间类
preloadClass("java.time.LocalDate");
preloadClass("java.time.LocalTime");
preloadClass("java.time.LocalDateTime");
preloadClass("java.time.ZonedDateTime");
preloadClass("java.time.OffsetDateTime");
preloadClass("java.time.OffsetTime");
preloadClass("java.time.Instant");
preloadClass("java.time.Duration");
preloadClass("java.time.Period");
preloadClass("java.time.Year");
preloadClass("java.time.YearMonth");
preloadClass("java.time.MonthDay");
preloadClass("java.time.Month");
preloadClass("java.time.DayOfWeek");


     // 时区相关
     preloadClass("java.time.ZoneId");
     preloadClass("java.time.ZoneOffset");
     preloadClass("java.time.Clock");
     
     // 格式化相关
     preloadClass("java.time.format.DateTimeFormatter");
     preloadClass("java.time.format.DateTimeFormatterBuilder");
     preloadClass("java.time.format.ResolverStyle");
     
     // 接口
     preloadInterface("java.time.temporal.TemporalAccessor");
     preloadInterface("java.time.temporal.Temporal");
     preloadInterface("java.time.temporal.TemporalAdjuster");
     preloadInterface("java.time.temporal.TemporalAmount");
     preloadInterface("java.time.temporal.TemporalField");
     preloadInterface("java.time.temporal.TemporalUnit");
     
     // 工具类
     preloadClass("java.time.temporal.ChronoField");
     preloadClass("java.time.temporal.ChronoUnit");
     preloadClass("java.time.temporal.TemporalAdjusters");
     
     log.info("成功加载java.time包类型声明");
 } catch (Exception e) {
     log.error("预加载java.time类型失败: {}", e.getMessage());
 }
}

private void preloadClass(String className) {
try {
Class<?> clazz = Class.forName(className);
ReflectionClassDeclaration declaration = new ReflectionClassDeclaration(clazz, this);
String simpleName = className.substring(className.lastIndexOf('.') + 1);
timeTypes.put(className, declaration);
timeTypes.put(simpleName, declaration);
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
timeTypes.put(interfaceName, declaration);
timeTypes.put(simpleName, declaration);
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
if (timeTypes.containsKey(name)) {
return SymbolReference.solved(timeTypes.get(name));
}

 // 尝试加载未预加载的java.time类型
 if (name.startsWith("java.time.")) {
     try {
         Class<?> clazz = Class.forName(name);
         if (clazz.isInterface()) {
             ReflectionInterfaceDeclaration declaration = new ReflectionInterfaceDeclaration(clazz, this);
             timeTypes.put(name, declaration);
             String simpleName = name.substring(name.lastIndexOf('.') + 1);
             timeTypes.put(simpleName, declaration);
             return SymbolReference.solved(declaration);
         } else {
             ReflectionClassDeclaration declaration = new ReflectionClassDeclaration(clazz, this);
             timeTypes.put(name, declaration);
             String simpleName = name.substring(name.lastIndexOf('.') + 1);
             timeTypes.put(simpleName, declaration);
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

