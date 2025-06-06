package io.github.jitawangzi.jdepend.project;

import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SimpleJavaProjectParser implements ProjectParser {
    private static final Logger log = LoggerFactory.getLogger(SimpleJavaProjectParser.class);

    @Override
    public boolean isApplicable(File projectRoot) {
        // 如果不是Maven或Gradle项目，但包含Java源文件，则视为普通Java项目
        return !isMavenProject(projectRoot) && !isGradleProject(projectRoot) && hasJavaSourceFiles(projectRoot);
    }
    
    private boolean isMavenProject(File projectRoot) {
        return new File(projectRoot, "pom.xml").exists();
    }
    
    private boolean isGradleProject(File projectRoot) {
        return new File(projectRoot, "build.gradle").exists() || 
               new File(projectRoot, "build.gradle.kts").exists() ||
               new File(projectRoot, "settings.gradle").exists() ||
               new File(projectRoot, "settings.gradle.kts").exists();
    }
    
    private boolean hasJavaSourceFiles(File dir) {
        if (dir.isFile() && dir.getName().endsWith(".java")) {
            return true;
        }
        
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (hasJavaSourceFiles(file)) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }

    @Override
    public void addSourceDirectories(CombinedTypeSolver typeSolver, File projectRoot) throws Exception {
        // 对于普通Java项目，我们需要找到所有可能的源码目录
        findJavaSourceDirectories(typeSolver, projectRoot);
    }
    
    private void findJavaSourceDirectories(CombinedTypeSolver typeSolver, File dir) {
        // 常见的源码目录名称
        List<String> commonSourceDirNames = List.of("src", "source", "java", "main");
        
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                boolean hasJavaFiles = false;
                for (File file : files) {
                    if (file.isFile() && file.getName().endsWith(".java")) {
                        hasJavaFiles = true;
                        break;
                    }
                }
                
                if (hasJavaFiles) {
                    // 如果目录中直接包含Java文件，则添加该目录
                    typeSolver.add(new JavaParserTypeSolver(dir));
                    log.debug("Added source directory: {}", dir.getAbsolutePath());
                } else {
                    // 递归检查子目录
                    for (File file : files) {
                        if (file.isDirectory()) {
                            // 优先检查常见的源码目录
                            if (commonSourceDirNames.contains(file.getName().toLowerCase())) {
                                findJavaSourceDirectories(typeSolver, file);
                            } else {
                                // 其他目录也检查，但优先级较低
                                findJavaSourceDirectories(typeSolver, file);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public List<File> resolveDependencies(File projectRoot) throws Exception {
        // 对于普通Java项目，尝试查找lib目录或其他常见的依赖目录
        List<File> jars = new ArrayList<>();
        
        // 检查常见的依赖目录
        List<String> commonLibDirNames = List.of("lib", "libs", "jars", "dependencies");
        
        for (String libDirName : commonLibDirNames) {
            File libDir = new File(projectRoot, libDirName);
            if (libDir.exists() && libDir.isDirectory()) {
                // 添加所有JAR文件
                File[] files = libDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
                if (files != null) {
                    for (File jar : files) {
                        if (jar.isFile()) {
                            jars.add(jar);
                        }
                    }
                }
            }
        }
        
        // 递归搜索项目中的所有JAR文件（可能会找到很多无关的JAR，但这是最后的尝试）
        if (jars.isEmpty()) {
            findAllJars(projectRoot, jars);
        }
        
        return jars;
    }
    
    private void findAllJars(File dir, List<File> jars) {
        if (dir.isDirectory()) {
            // 忽略某些目录
            String dirName = dir.getName().toLowerCase();
            if (dirName.equals("target") || dirName.equals("build") || dirName.equals(".git") || dirName.equals(".svn")) {
                return;
            }
            
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().toLowerCase().endsWith(".jar")) {
                        jars.add(file);
                    } else if (file.isDirectory()) {
                        findAllJars(file, jars);
                    }
                }
            }
        }
    }
}

