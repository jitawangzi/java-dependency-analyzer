package io.github.jitawangzi.jdepend.core.solver;

import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.model.SymbolReference;
import com.github.javaparser.resolution.TypeSolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**

组合式JDK模块解析器，整合所有JDK特定解析器
*/
public class CombinedJdkModuleSolver implements TypeSolver {
private static final Logger log = LoggerFactory.getLogger(CombinedJdkModuleSolver.class);
private final List<TypeSolver> solvers = new ArrayList<>();
private TypeSolver parent;

public CombinedJdkModuleSolver() {
log.info("初始化组合式JDK模块解析器");


 // 添加所有特定的JDK模块解析器
 solvers.add(new SimpleXmlTypeSolver());
 solvers.add(new JavaUtilTypeSolver());
 solvers.add(new JavaIOTypeSolver());
 solvers.add(new JavaTimeTypeSolver());
 solvers.add(new JavaNetTypeSolver());
 
 log.info("成功加载所有JDK模块解析器");
}

/**

添加自定义解析器
*/
public void addSolver(TypeSolver solver) {
solvers.add(solver);
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
// 依次尝试所有特定解析器
for (TypeSolver solver : solvers) {
SymbolReference<ResolvedReferenceTypeDeclaration> reference = solver.tryToSolveType(name);
if (reference.isSolved()) {
return reference;
}
}


 // 如果所有特定解析器都无法解析，尝试使用父解析器
 if (parent != null) {
     return parent.tryToSolveType(name);
 }
 
 return SymbolReference.unsolved(ResolvedReferenceTypeDeclaration.class);
}
}
