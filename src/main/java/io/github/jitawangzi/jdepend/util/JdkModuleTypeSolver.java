package io.github.jitawangzi.jdepend.util;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.model.SymbolReference;
import com.github.javaparser.symbolsolver.reflectionmodel.ReflectionClassDeclaration;

/**
 * 通用JDK模块类型解析器，能够处理任何JDK模块中的类
 */
@Deprecated
public class JdkModuleTypeSolver implements TypeSolver {
    private static final Logger log = LoggerFactory.getLogger(JdkModuleTypeSolver.class);
    private TypeSolver parent;
    
    // 类名到类对象的缓存
    private final Map<String, Class<?>> classCache = new ConcurrentHashMap<>();
    
    // 记录已尝试但未找到的类，避免重复尝试
    private final Map<String, Boolean> notFoundCache = new ConcurrentHashMap<>();
    
	// 当前正在解析的类型，用于防止递归循环
	private final ThreadLocal<Map<String, Boolean>> processingTypes = ThreadLocal.withInitial(ConcurrentHashMap::new);

    // 常见的JDK包前缀，用于快速识别JDK类
    private static final List<String> JDK_PACKAGES = Arrays.asList(
        "java.", 
        "javax.", 
        "javafx.", 
        "com.sun.", 
        "org.w3c.", 
        "org.xml.", 
        "sun."
    );
    
    public JdkModuleTypeSolver() {
        log.info("初始化通用JDK模块类型解析器");
        
        // 预加载一些经常使用的基础类
        preloadCommonClasses();
    }
    
    /**
     * 预加载常用类
     */
    private void preloadCommonClasses() {
        // 基础类
        preloadClass("java.lang.Object");
        preloadClass("java.lang.String");
        preloadClass("java.lang.Integer");
        preloadClass("java.lang.Boolean");
        
        // 集合类
        preloadClass("java.util.List");
        preloadClass("java.util.Map");
        preloadClass("java.util.Set");
        preloadClass("java.util.ArrayList");
        preloadClass("java.util.HashMap");
        
        // IO类
        preloadClass("java.io.File");
        preloadClass("java.io.InputStream");
        preloadClass("java.io.OutputStream");
        
        // XML相关类
        preloadClass("org.w3c.dom.Element");
        preloadClass("org.w3c.dom.Document");
        preloadClass("org.w3c.dom.Node");
        preloadClass("javax.xml.parsers.DocumentBuilderFactory");
        preloadClass("javax.xml.parsers.DocumentBuilder");
        
        log.info("预加载了 {} 个常用JDK类", classCache.size());
    }
    
    /**
     * 预加载单个类
     */
    private void preloadClass(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            classCache.put(className, clazz);
            
            // 也缓存简单类名
            String simpleName = className.substring(className.lastIndexOf('.') + 1);
            classCache.put(simpleName, clazz);
        } catch (ClassNotFoundException e) {
            log.warn("预加载类失败: {}", className);
            notFoundCache.put(className, true);
        }
    }
    
    @Override
    public TypeSolver getParent() {
        return parent;
    }
    
    @Override
    public void setParent(TypeSolver parent) {
        this.parent = parent;
    }
    
    @Override
    public SymbolReference<ResolvedReferenceTypeDeclaration> tryToSolveType(String name) {
		// 防止递归循环
		Map<String, Boolean> currentlyProcessing = processingTypes.get();
		if (currentlyProcessing.containsKey(name)) {
			// 如果当前线程已经在处理这个类型，直接返回未解决，避免递归
			return SymbolReference.unsolved(ResolvedReferenceTypeDeclaration.class);
        }
        
		try {
			// 标记当前正在处理此类型
			currentlyProcessing.put(name, true);

			// 检查是否在缓存中
			if (classCache.containsKey(name)) {
				try {
					return SymbolReference.solved(new ReflectionClassDeclaration(classCache.get(name), getRoot()));
				} catch (Exception e) {
					log.debug("从缓存解析 {} 失败: {}", name, e.getMessage());
				}
            }

			// 检查是否已知不存在
			if (notFoundCache.containsKey(name)) {
				// 不再调用父解析器，直接返回未解决
				return SymbolReference.unsolved(ResolvedReferenceTypeDeclaration.class);
            }

			// 检查是否看起来像JDK类
			boolean looksLikeJdkClass = false;
			for (String prefix : JDK_PACKAGES) {
				if (name.startsWith(prefix)) {
					looksLikeJdkClass = true;
					break;
                }
            }

			if (looksLikeJdkClass) {
				// 尝试动态加载类
                try {
					Class<?> clazz = Class.forName(name);
					classCache.put(name, clazz); // 缓存起来，以备将来使用

					// 如果是简单类名，也缓存一下
					if (!name.contains(".")) {
						String fullName = clazz.getName();
						classCache.put(fullName, clazz);
					}
                    
                    return SymbolReference.solved(
                        new ReflectionClassDeclaration(clazz, getRoot()));
                } catch (ClassNotFoundException e) {
					notFoundCache.put(name, true);
					log.debug("无法加载JDK类: {}", name);
                } catch (Exception e) {
					log.debug("解析类型 {} 时出错: {}", name, e.getMessage());
				}
			}

			// 尝试通过简单类名查找JDK类
			if (!name.contains(".")) {
				for (String prefix : JDK_PACKAGES) {
					try {
						String fullName = prefix + name;
						Class<?> clazz = Class.forName(fullName);
						classCache.put(name, clazz); // 缓存简单名称
						classCache.put(fullName, clazz); // 缓存全名

						return SymbolReference.solved(new ReflectionClassDeclaration(clazz, getRoot()));
					} catch (ClassNotFoundException e) {
						// 尝试下一个包前缀
					} catch (Exception e) {
						log.debug("解析类型 {} 时出错: {}", prefix + name, e.getMessage());
					}
                }
            }

			// 我们自己解析失败，但不再调用父解析器，避免递归循环
			return SymbolReference.unsolved(ResolvedReferenceTypeDeclaration.class);

		} finally {
			// 清除处理标记
			currentlyProcessing.remove(name);
        }
    }
    
	public TypeSolver getRoot() {
        if (parent == null) {
            return this;
        }
        return parent.getRoot();
    }
    
    /**
     * 手动添加类映射
     */
    public void addClass(String name, Class<?> clazz) {
        classCache.put(name, clazz);
        // 如果是全限定名，也添加简单名称的映射
        if (name.contains(".")) {
            String simpleName = name.substring(name.lastIndexOf('.') + 1);
            classCache.put(simpleName, clazz);
        }
    }
    
    /**
     * 获取已缓存的类数量
     */
    public int getCachedClassCount() {
        return classCache.size();
    }
    
    /**
     * 清除缓存
     */
    public void clearCache() {
        classCache.clear();
        notFoundCache.clear();
        preloadCommonClasses();
    }
}