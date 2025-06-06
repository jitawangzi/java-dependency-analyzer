package io.github.jitawangzi.jdepend.project;

import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import java.io.File;
import java.util.List;

public interface ProjectParser {
    /**
     * 判断该解析器是否适用于给定项目
     * @param projectRoot 项目根目录
     * @return 是否适用
     */
    boolean isApplicable(File projectRoot);
    
    /**
     * 添加项目及其所有模块的源码目录到类型解析器
     * @param typeSolver 类型解析器
     * @param projectRoot 项目根目录
     * @throws Exception 解析异常
     */
    void addSourceDirectories(CombinedTypeSolver typeSolver, File projectRoot) throws Exception;
    
    /**
     * 解析项目的所有依赖，返回依赖的JAR文件列表
     * @param projectRoot 项目根目录
     * @return 依赖的JAR文件列表
     * @throws Exception 解析异常
     */
    List<File> resolveDependencies(File projectRoot) throws Exception;
}

