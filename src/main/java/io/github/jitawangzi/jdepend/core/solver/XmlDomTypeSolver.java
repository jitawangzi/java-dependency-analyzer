package io.github.jitawangzi.jdepend.core.solver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.model.SymbolReference;
import com.github.javaparser.symbolsolver.reflectionmodel.ReflectionInterfaceDeclaration;

/**
 * 专门用于解析XML DOM接口的TypeSolver
 */
@Deprecated
public class XmlDomTypeSolver implements TypeSolver {
	private static final Logger log = LoggerFactory.getLogger(XmlDomTypeSolver.class);
    private TypeSolver parent;
    
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
		// 直接处理Element接口及相关接口
		if ("Element".equals(name) || "org.w3c.dom.Element".equals(name)) {
			try {
				Class<?> clazz = Class.forName("org.w3c.dom.Element");
				// 使用ReflectionInterfaceDeclaration而不是ReflectionClassDeclaration
				return SymbolReference.solved(new ReflectionInterfaceDeclaration(clazz, getRoot()));
			} catch (Exception e) {
				log.debug("无法解析Element接口: {}", e.getMessage());
			}
        }
        
		// 处理其他常见的XML DOM接口
		if ("Document".equals(name) || "org.w3c.dom.Document".equals(name)) {
			try {
				Class<?> clazz = Class.forName("org.w3c.dom.Document");
				return SymbolReference.solved(new ReflectionInterfaceDeclaration(clazz, getRoot()));
			} catch (Exception e) {
				log.debug("无法解析Document接口: {}", e.getMessage());
            }
		}

		if ("Node".equals(name) || "org.w3c.dom.Node".equals(name)) {
			try {
				Class<?> clazz = Class.forName("org.w3c.dom.Node");
				return SymbolReference.solved(new ReflectionInterfaceDeclaration(clazz, getRoot()));
			} catch (Exception e) {
				log.debug("无法解析Node接口: {}", e.getMessage());
            }
        }

		// 如果我们不能解析，让父解析器尝试
		if (parent != null) {
			return parent.tryToSolveType(name);
		}

		return SymbolReference.unsolved(ResolvedReferenceTypeDeclaration.class);
    }
    
	public TypeSolver getRoot() {
        if (parent == null) {
            return this;
        }
        return parent.getRoot();
    }
}