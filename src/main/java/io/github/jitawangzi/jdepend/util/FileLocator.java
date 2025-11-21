package io.github.jitawangzi.jdepend.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.jitawangzi.jdepend.config.AppConfigManager;
import io.github.jitawangzi.jdepend.project.ProjectParser;
import io.github.jitawangzi.jdepend.project.ProjectParserFactory;

/**
 * 文件定位器，用于定位Java类文件
 */
public class FileLocator {
    private static final Logger log = LoggerFactory.getLogger(FileLocator.class);
    private final PathMatcher javaMatcher;
	private static FileLocator INSTANCE;
    
    // 缓存已发现的源码目录路径
    private final Set<Path> sourceDirectories = Collections.synchronizedSet(new HashSet<>());
    
    // 缓存类名到文件路径的映射，提高重复查找性能
    private final Map<String, Path> classPathCache = new ConcurrentHashMap<>();
    
    // 项目解析器
    private ProjectParser projectParser;
    
    // 项目根目录
    private final Path projectRootPath;
    
	/**
	 * 获取单例实例
	 * 
	 * @return FileLocator实例
	 */
	public static FileLocator getInstance() {
		if (INSTANCE == null) {
			synchronized (FileLocator.class) {
				if (INSTANCE == null) {
					INSTANCE = new FileLocator();
				}
			}
		}
		return INSTANCE;
	}
    /**
     * 构造函数
     */
	private FileLocator() {
        this.javaMatcher = FileSystems.getDefault().getPathMatcher("glob:**.java");
        this.projectRootPath = Paths.get(AppConfigManager.get().getProjectRootPath());
        
        // 初始化项目解析器
        try {
            File projectRoot = new File(AppConfigManager.get().getProjectRootPath());
            this.projectParser = ProjectParserFactory.getParser(projectRoot);
            if (this.projectParser == null) {
                log.warn("Could not determine project type. Falling back to basic file search.");
            } else {
//                log.info("Using project parser: {}", projectParser.getClass().getSimpleName());
                // 预先扫描并缓存源码目录
                scanSourceDirectories();
            }
        } catch (Exception e) {
            log.error("Error initializing project parser", e);
        }
    }
    
    /**
     * 扫描项目中的所有源码目录
     */
    private void scanSourceDirectories() {
        try {
            // 记录开始时间
            long startTime = System.currentTimeMillis();
            
            // 扫描源码目录
            if (projectParser != null) {
                collectSourceDirectories();
            } else {
                // 回退到基本的源码目录扫描
                collectBasicSourceDirectories();
            }
            
            log.info("Found {} source directories in {} ms", 
                    sourceDirectories.size(), 
                    System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("Error scanning source directories", e);
        }
    }
    
    /**
     * 根据项目类型收集源码目录
     */
    private void collectSourceDirectories() throws IOException {
        // 方法1：基于Java文件的包声明推断源码目录
        Set<Path> sourceDirs = inferSourceDirectoriesFromPackages();
        
        // 方法2：查找所有pom.xml文件并检查标准源码目录
        if (sourceDirs.isEmpty() || sourceDirs.size() < 2) {
            findSourceDirsFromProjectFiles(sourceDirs);
        }
        
        // 方法3：通用检测（如果前面的方法没有找到足够的源码目录）
        if (sourceDirs.isEmpty() || sourceDirs.size() < 2) {
            findSourceDirsUsingConventions(sourceDirs);
        }
        
        // 将收集到的源码目录添加到主集合
        sourceDirectories.addAll(sourceDirs);
        
        // 记录找到的源码目录
        if (sourceDirectories.isEmpty()) {
            log.warn("No source directories found. File location may not work correctly.");
        } else {
            log.info("Found the following source directories:");
            sourceDirectories.forEach(dir -> log.info(" - {}", dir));
        }
    }
    
    /**
     * 通过解析Java文件的包声明来推断源码目录
     */
    private Set<Path> inferSourceDirectoriesFromPackages() throws IOException {
        Set<Path> sourceDirs = new HashSet<>();
        Map<String, Set<Path>> packageToFiles = new HashMap<>();
        
        // 找到所有Java文件并按包名分组
        Files.walkFileTree(projectRootPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String fileName = file.getFileName().toString();
                if (fileName.endsWith(".java")) {
                    try {
                        // 解析Java文件的包名
                        String packageName = extractPackageName(file);
                        if (packageName != null && !packageName.isEmpty()) {
                            packageToFiles.computeIfAbsent(packageName, k -> new HashSet<>()).add(file);
                        }
                    } catch (Exception e) {
                        // 忽略无法解析包名的文件
                    }
                }
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                // 忽略隐藏目录和构建目录
                String dirName = dir.getFileName().toString();
                if (dirName.startsWith(".") || 
                    dirName.equals("target") || 
                    dirName.equals("build") || 
                    dirName.equals("bin") ||
                    dirName.equals("dist") ||
                    dirName.equals("node_modules")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
        });
        
        // 对于每个包，推断源码目录
        for (Map.Entry<String, Set<Path>> entry : packageToFiles.entrySet()) {
            String packageName = entry.getKey();
            Set<Path> files = entry.getValue();
            
            // 包路径，如 "com/example/app"
            String packagePath = packageName.replace('.', '/');
            
            for (Path file : files) {
                // 获取文件的完整路径字符串（统一使用正斜杠）
                String filePathStr = file.toString().replace('\\', '/');
                
                // 查找包路径在文件路径中的位置
                int index = filePathStr.lastIndexOf("/" + packagePath + "/");
                if (index >= 0) {
                    // 源码目录是包路径之前的部分
                    String sourceRootStr = filePathStr.substring(0, index);
                    Path sourceRoot = Paths.get(sourceRootStr);
                    if (Files.isDirectory(sourceRoot)) {
                        sourceDirs.add(sourceRoot);
                    }
                }
            }
        }
        
        return sourceDirs;
    }
    
    /**
     * 从Java文件中提取包名
     */
    private String extractPackageName(Path javaFile) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(javaFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("package ")) {
                    // 提取包名，去掉"package "前缀和结尾的分号
                    return line.substring(8, line.endsWith(";") ? line.length() - 1 : line.length()).trim();
                } else if (!line.isEmpty() && !line.startsWith("//") && !line.startsWith("/*")) {
                    // 如果遇到非空行且不是注释，且还没找到package语句，则停止搜索
                    break;
                }
            }
        }
        return null;
    }
    
    /**
     * 查找项目文件（pom.xml, build.gradle等）并检查标准源码目录
     */
    private void findSourceDirsFromProjectFiles(Set<Path> sourceDirs) throws IOException {
        // 查找所有pom.xml文件（Maven项目）
        Set<Path> pomFiles = new HashSet<>();
        
        Files.walkFileTree(projectRootPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String fileName = file.getFileName().toString();
                if (fileName.equals("pom.xml")) {
                    pomFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                // 忽略隐藏目录和构建目录
                String dirName = dir.getFileName().toString();
                if (dirName.startsWith(".") || 
                    dirName.equals("target") || 
                    dirName.equals("build") || 
                    dirName.equals("bin")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
        });
        
        // 对于每个pom文件，检查标准源码目录
        for (Path pomFile : pomFiles) {
            Path moduleDir = pomFile.getParent();
            
            // 检查Maven标准源码目录
            checkStandardDir(sourceDirs, moduleDir, "src/main/java");
            checkStandardDir(sourceDirs, moduleDir, "src/main/kotlin");
            checkStandardDir(sourceDirs, moduleDir, "src/main/groovy");
            
            // 也可以尝试读取pom.xml来获取自定义sourceDirectory配置
            // 这里简化处理，不解析XML
        }
        
        // 查找所有build.gradle文件（Gradle项目）
        Set<Path> gradleFiles = new HashSet<>();
        
        Files.walkFileTree(projectRootPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String fileName = file.getFileName().toString();
                if (fileName.equals("build.gradle") || fileName.equals("build.gradle.kts")) {
                    gradleFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                // 忽略隐藏目录和构建目录
                String dirName = dir.getFileName().toString();
                if (dirName.startsWith(".") || 
                    dirName.equals("build") || 
                    dirName.equals("bin")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
        });
        
        // 对于每个Gradle文件，检查标准源码目录
        for (Path gradleFile : gradleFiles) {
            Path moduleDir = gradleFile.getParent();
            
            // 检查Gradle标准源码目录
            checkStandardDir(sourceDirs, moduleDir, "src/main/java");
            checkStandardDir(sourceDirs, moduleDir, "src/main/kotlin");
            checkStandardDir(sourceDirs, moduleDir, "src/main/groovy");
            
            // 也可以尝试解析build.gradle来获取自定义sourceSet配置
            // 这里简化处理，不解析Gradle文件
        }
    }
    
    /**
     * 检查标准目录是否存在并添加到源码目录集合
     */
    private void checkStandardDir(Set<Path> sourceDirs, Path baseDir, String relativePath) {
        Path dir = baseDir.resolve(relativePath);
        if (Files.exists(dir) && Files.isDirectory(dir)) {
            sourceDirs.add(dir);
            log.debug("Found standard source directory: {}", dir);
        }
    }
    
    /**
     * 使用通用约定查找可能的源码目录
     */
    private void findSourceDirsUsingConventions(Set<Path> sourceDirs) throws IOException {
        // 常见的源码目录名称和模式
        final List<String> sourcePatterns = List.of("src", "source", "java", "main", "app", "core");
        
        Files.walkFileTree(projectRootPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                // 忽略隐藏目录和构建目录
                String dirName = dir.getFileName().toString();
                if (dirName.startsWith(".") || 
                    dirName.equals("target") || 
                    dirName.equals("build") || 
                    dirName.equals("bin") ||
                    dirName.equals("dist") ||
                    dirName.equals("node_modules")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                
                // 检查目录名是否匹配常见源码目录模式
                boolean isSourcePattern = sourcePatterns.contains(dirName.toLowerCase());
                
                if (isSourcePattern) {
                    // 检查是否包含Java文件
                    try {
                        boolean hasJavaFiles = Files.list(dir)
                            .anyMatch(p -> p.toString().endsWith(".java"));
                        
                        if (hasJavaFiles) {
                            sourceDirs.add(dir);
                            log.debug("Found potential source directory by convention: {}", dir);
                        }
                    } catch (IOException e) {
                        // 忽略IO异常
                    }
                }
                
                // 检查子目录数量
                try {
                    long subdirCount = Files.list(dir)
                        .filter(Files::isDirectory)
                        .count();
                    
                    // 如果目录有很多子目录，通常不是源码目录
                    if (subdirCount > 10) {
                        return FileVisitResult.CONTINUE;
                    }
                } catch (IOException e) {
                    // 忽略IO异常
                }
                
                return FileVisitResult.CONTINUE;
            }
        });
    }
    
    /**
     * 基本的源码目录收集方法，用于当项目解析器不可用时
     */
    private void collectBasicSourceDirectories() throws IOException {
        Files.walkFileTree(projectRootPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                // 忽略隐藏目录和二进制目录
                String dirName = dir.getFileName().toString();
                if (dirName.startsWith(".") || 
                    dirName.equals("target") || 
                    dirName.equals("build") || 
                    dirName.equals("bin") || 
                    dirName.equals("out")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                
                // 检查目录是否包含.java文件
                try {
                    boolean hasJavaFiles = Files.list(dir)
                        .anyMatch(file -> file.toString().endsWith(".java"));
                    
                    if (hasJavaFiles) {
                        sourceDirectories.add(dir);
                    }
                } catch (IOException e) {
                    // 忽略异常，继续搜索
                }
                
                return FileVisitResult.CONTINUE;
            }
        });
    }
    
    /**
     * 检查是否可能是源码目录
     */
    private boolean isLikelySourceDirectory(Path dir) {
        String path = dir.toString().toLowerCase();
        
        // Maven/Gradle标准源码目录
        if (path.endsWith("/src/main/java") || 
            path.endsWith("\\src\\main\\java") ||
            path.endsWith("/src/main/kotlin") || 
            path.endsWith("\\src\\main\\kotlin") ||
            path.endsWith("/src/main/groovy") || 
            path.endsWith("\\src\\main\\groovy")) {
            return true;
        }
        
        // 其他可能的源码目录
        if (path.endsWith("/src") || path.endsWith("\\src") ||
            path.endsWith("/source") || path.endsWith("\\source") ||
            path.endsWith("/java") || path.endsWith("\\java")) {
            
            // 检查该目录是否包含.java文件
            try {
                return Files.list(dir)
                      .anyMatch(file -> file.toString().endsWith(".java"));
            } catch (IOException e) {
                return false;
            }
        }
        
        return false;
    }
    
    /**
     * 定位类文件
     * 
     * @param className 类名
     * @return 类文件路径，如果找不到则返回null
     * @throws IOException 如果定位过程中发生IO错误
     */
    public Path locate(String className) throws IOException {
        // 检查缓存
        if (classPathCache.containsKey(className)) {
            return classPathCache.get(className);
        }
        
        String relativePath = className.replace('.', '/') + ".java";
        
        // 首先，在已知的源码目录中快速查找
        if (!sourceDirectories.isEmpty()) {
            for (Path sourceDir : sourceDirectories) {
                Path potentialFilePath = sourceDir.resolve(relativePath);
                if (Files.exists(potentialFilePath)) {
                    // 缓存结果
                    classPathCache.put(className, potentialFilePath);
                    return potentialFilePath;
                }
            }
        }
        
        // 如果在源码目录中没有找到，则进行全项目搜索
        List<Path> matches = new ArrayList<>();
        
        Files.walkFileTree(projectRootPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (javaMatcher.matches(file) && file.toString().endsWith(relativePath.replace('/', File.separatorChar))) {
                    matches.add(file);
                    return FileVisitResult.TERMINATE;
                }
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                // 忽略隐藏目录和构建目录
                String dirName = dir.getFileName().toString();
                if (dirName.startsWith(".") || 
                    dirName.equals("target") || 
                    dirName.equals("build") ||
                    dirName.equals("bin") ||
                    dirName.equals("out")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
        });
        
        if (!matches.isEmpty()) {
            Path result = matches.get(0);
            // 缓存结果
            classPathCache.put(className, result);
            return result;
        }
        
        return null;
    }
    
    /**
     * 根据包名查找该包下的所有Java类
     * 
     * @param packageName 包名
     * @return 包下的所有Java类路径列表
     * @throws IOException 如果查找过程中发生IO错误
     */
    public List<Path> findClassesInPackage(String packageName) throws IOException {
        String packagePath = packageName.replace('.', '/');
        List<Path> result = new ArrayList<>();
        
        // 如果源码目录已经扫描，优先在源码目录中查找
        if (!sourceDirectories.isEmpty()) {
            for (Path sourceDir : sourceDirectories) {
                Path packageDir = sourceDir.resolve(packagePath);
                if (Files.exists(packageDir) && Files.isDirectory(packageDir)) {
                    try {
                        List<Path> packageFiles = Files.list(packageDir)
                            .filter(path -> path.toString().endsWith(".java"))
                            .collect(Collectors.toList());
                        result.addAll(packageFiles);
                    } catch (IOException e) {
                        log.warn("Error listing files in package directory: {}", packageDir, e);
                    }
                }
            }
            
            if (!result.isEmpty()) {
                return result;
            }
        }
        
        // 全局搜索
        Files.walkFileTree(projectRootPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                // 忽略隐藏目录和构建目录
                String dirName = dir.getFileName().toString();
                if (dirName.startsWith(".") || 
                    dirName.equals("target") || 
                    dirName.equals("build") ||
                    dirName.equals("bin") ||
                    dirName.equals("out")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                
                // 如果目录路径末尾与包路径匹配
                String dirPath = dir.toString().replace(File.separatorChar, '/');
                if (dirPath.endsWith("/" + packagePath)) {
                    try {
                        Files.list(dir)
                            .filter(p -> p.toString().endsWith(".java"))
                            .forEach(result::add);
                    } catch (IOException e) {
                        log.warn("Error listing files in directory: {}", dir, e);
                    }
                }
                
                return FileVisitResult.CONTINUE;
            }
        });
        
        return result;
    }
    
    /**
     * 添加额外的源码目录
     * 
     * @param sourceDirs 源码目录列表
     */
    public void addSourceDirectories(List<String> sourceDirs) {
        if (sourceDirs != null && !sourceDirs.isEmpty()) {
            for (String sourceDir : sourceDirs) {
                Path path = Paths.get(sourceDir);
                if (Files.exists(path) && Files.isDirectory(path)) {
                    sourceDirectories.add(path);
                    log.debug("Added source directory: {}", path);
                } else {
                    log.warn("Source directory does not exist or is not a directory: {}", sourceDir);
                }
            }
        }
    }
    
    /**
     * 清除缓存
     */
    public void clearCache() {
        classPathCache.clear();
    }
    
    /**
     * 获取当前已知的源码目录
     * 
     * @return 源码目录列表
     */
    public List<Path> getSourceDirectories() {
        return new ArrayList<>(sourceDirectories);
    }
    
    /**
     * 强制重新扫描项目的源码目录
     */
    public void rescanSourceDirectories() {
        sourceDirectories.clear();
        scanSourceDirectories();
    }
}
