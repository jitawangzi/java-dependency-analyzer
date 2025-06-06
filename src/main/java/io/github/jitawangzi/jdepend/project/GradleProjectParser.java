package io.github.jitawangzi.jdepend.project;

import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GradleProjectParser implements ProjectParser {
    private static final Logger log = LoggerFactory.getLogger(GradleProjectParser.class);

    @Override
    public boolean isApplicable(File projectRoot) {
        // 检查是否存在build.gradle或build.gradle.kts文件
        File buildGradle = new File(projectRoot, "build.gradle");
        File buildGradleKts = new File(projectRoot, "build.gradle.kts");
        File settingsGradle = new File(projectRoot, "settings.gradle");
        File settingsGradleKts = new File(projectRoot, "settings.gradle.kts");
        
        return buildGradle.exists() || buildGradleKts.exists() || settingsGradle.exists() || settingsGradleKts.exists();
    }

    @Override
    public void addSourceDirectories(CombinedTypeSolver typeSolver, File projectRoot) throws Exception {
        if (!isApplicable(projectRoot)) {
            throw new IllegalArgumentException("Not a Gradle project: " + projectRoot.getAbsolutePath());
        }
        
        // 添加主项目的源码目录
        addProjectSourceDirectories(typeSolver, projectRoot);
        
        // 读取settings.gradle获取子模块信息
        File settingsGradle = new File(projectRoot, "settings.gradle");
        File settingsGradleKts = new File(projectRoot, "settings.gradle.kts");
        
        if (settingsGradle.exists() || settingsGradleKts.exists()) {
            File settingsFile = settingsGradle.exists() ? settingsGradle : settingsGradleKts;
            
            // 尝试解析include语句以识别子模块
            List<String> lines = Files.readAllLines(settingsFile.toPath());
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("include")) {
                    // 提取模块名称
                    String[] parts = line.substring(line.indexOf("'") + 1).split("'\\s*,\\s*'");
                    for (String part : parts) {
                        String moduleName = part.replace("'", "").replace("\"", "").trim();
                        if (moduleName.contains(":")) {
                            moduleName = moduleName.substring(moduleName.lastIndexOf(':') + 1);
                        }
                        
                        File moduleDir = new File(projectRoot, moduleName);
                        if (moduleDir.exists() && moduleDir.isDirectory()) {
                            // 递归处理子模块
                            addProjectSourceDirectories(typeSolver, moduleDir);
                        }
                    }
                }
            }
        }
    }
    
    private void addProjectSourceDirectories(CombinedTypeSolver typeSolver, File projectDir) {
        // Gradle默认源码目录
        List<String> sourcePaths = Arrays.asList(
            "src/main/java",
            "src/main/kotlin",
            "src/main/groovy"
        );
        
        for (String sourcePath : sourcePaths) {
            File sourceDir = new File(projectDir, sourcePath);
            if (sourceDir.exists() && sourceDir.isDirectory()) {
                typeSolver.add(new JavaParserTypeSolver(sourceDir));
                log.debug("Added source directory: {}", sourceDir.getAbsolutePath());
            }
        }
    }

    @Override
    public List<File> resolveDependencies(File projectRoot) throws Exception {
        if (!isApplicable(projectRoot)) {
            throw new IllegalArgumentException("Not a Gradle project: " + projectRoot.getAbsolutePath());
        }
        
        List<File> jars = new ArrayList<>();
        
        // 创建临时文件存储类路径
        File tempFile = File.createTempFile("gradle-classpath", ".txt");
        tempFile.deleteOnExit();
        
        // 创建一个临时的Gradle脚本来输出依赖
        File tempScript = File.createTempFile("print-classpath", ".gradle");
        tempScript.deleteOnExit();
        
        // 写入脚本内容
        Files.writeString(tempScript.toPath(), 
            "allprojects {\n" +
            "    task printClasspath {\n" +
            "        doLast {\n" +
            "            def file = new File('" + tempFile.getAbsolutePath().replace("\\", "\\\\") + "')\n" +
            "            if (project.configurations.findByName('runtimeClasspath')) {\n" +
            "                file.append(project.configurations.runtimeClasspath.asPath)\n" +
            "            } else if (project.configurations.findByName('compile')) {\n" +
            "                file.append(project.configurations.compile.asPath)\n" +
            "            }\n" +
            "        }\n" +
            "    }\n" +
            "}\n"
        );
        
        // 执行Gradle任务
        ProcessBuilder processBuilder = new ProcessBuilder();
        
        // 检查是否有Gradle包装器
        File gradlew = new File(projectRoot, isWindows() ? "gradlew.bat" : "gradlew");
        if (gradlew.exists() && gradlew.canExecute()) {
            processBuilder.command(gradlew.getAbsolutePath(), "-I", tempScript.getAbsolutePath(), "printClasspath");
        } else {
            processBuilder.command("gradle", "-I", tempScript.getAbsolutePath(), "printClasspath");
        }
        
        processBuilder.directory(projectRoot);
        Process process = processBuilder.start();
        
        // 读取输出
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new Exception("Gradle command failed with exit code: " + exitCode + "\nOutput: " + output);
        }
        
        // 从文件读取依赖路径
        if (tempFile.exists() && tempFile.length() > 0) {
            String classpath = Files.readString(tempFile.toPath()).trim();
            String pathSeparator = System.getProperty("path.separator");
            
            for (String path : classpath.split(pathSeparator)) {
                if (path.trim().endsWith(".jar")) {
                    File jarFile = new File(path.trim());
                    if (jarFile.exists() && jarFile.isFile()) {
                        jars.add(jarFile);
                    }
                }
            }
        }
        
        return jars;
    }
    
    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}

