package io.github.jitawangzi.jdepend.project;

import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.ModelSource;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.resolution.ModelResolver;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class MavenProjectParser implements ProjectParser {
    private static final Logger log = LoggerFactory.getLogger(MavenProjectParser.class);

    @Override
    public boolean isApplicable(File projectRoot) {
        File pomFile = new File(projectRoot, "pom.xml");
        return pomFile.exists() && pomFile.isFile();
    }

    @Override
    public void addSourceDirectories(CombinedTypeSolver typeSolver, File projectRoot) throws Exception {
        File pomFile = new File(projectRoot, "pom.xml");
        if (!pomFile.exists()) {
            throw new IllegalArgumentException("Not a Maven project: " + projectRoot.getAbsolutePath());
        }
        
        // 添加当前项目的源码目录
        addSourceDirectory(typeSolver, projectRoot);

        // 读取POM文件
        try (FileReader reader = new FileReader(pomFile)) {
            MavenXpp3Reader mavenReader = new MavenXpp3Reader();
            Model model = mavenReader.read(reader);

            // 处理模块
            if (model.getModules() != null) {
                for (String moduleName : model.getModules()) {
                    File moduleDir;
                    if (moduleName.startsWith("../")) {
                        // 处理上级目录的模块
                        moduleDir = new File(projectRoot.getParentFile(), moduleName.substring(3));
                    } else {
                        // 处理子目录的模块
                        moduleDir = new File(projectRoot, moduleName);
                    }

                    File modulePomFile = new File(moduleDir, "pom.xml");
                    if (modulePomFile.exists()) {
                        // 递归处理模块
                        addSourceDirectories(typeSolver, moduleDir);
                    } else {
                        log.warn("Module POM file not found: " + modulePomFile.getAbsolutePath());
                    }
                }
            }
        }
    }

    /**
     * 添加单个目录的源码路径
     */
    private void addSourceDirectory(CombinedTypeSolver typeSolver, File projectDir) {
        File srcMainJava = new File(projectDir, "src/main/java");
        if (srcMainJava.exists() && srcMainJava.isDirectory()) {
            typeSolver.add(new JavaParserTypeSolver(srcMainJava));
            log.debug("Added source directory: {}", srcMainJava.getAbsolutePath());
        }
    }

    @Override
    public List<File> resolveDependencies(File projectRoot) throws Exception {
        File pomFile = new File(projectRoot, "pom.xml");
        if (!pomFile.exists()) {
            throw new IllegalArgumentException("Not a Maven project: " + projectRoot.getAbsolutePath());
        }
        
        List<File> jars = new ArrayList<>();

        // 检查Maven Home
        String mavenHome = System.getenv("M2_HOME");
        if (mavenHome == null || mavenHome.isEmpty()) {
            mavenHome = System.getenv("MAVEN_HOME");
        }

        if (mavenHome == null || mavenHome.isEmpty()) {
            throw new IllegalStateException("Maven home not found. Please set M2_HOME or MAVEN_HOME environment variable.");
        }

        // 创建临时文件存储类路径
        File tempFile = File.createTempFile("maven-classpath", ".txt");
        tempFile.deleteOnExit();

        // 配置Maven命令
        InvocationRequest request = new DefaultInvocationRequest();
        request.setPomFile(pomFile);
        // 使用mdep.outputFile参数直接将依赖写入文件
        request.setGoals(List.of("dependency:build-classpath", "-Dmdep.outputFile=" + tempFile.getAbsolutePath()));
        request.setBatchMode(true);
        request.setQuiet(true); // 设置为安静模式,不要控制台输出

        // 执行Maven命令
        Invoker invoker = new DefaultInvoker();
        invoker.setMavenHome(new File(mavenHome));
        InvocationResult result = invoker.execute(request);

        if (result.getExitCode() != 0) {
            throw new Exception("Maven command failed with exit code: " + result.getExitCode());
        }

        // 直接从文件读取依赖列表
        String classpath = Files.readString(tempFile.toPath()).trim();
        // 获取系统特定的路径分隔符
        String pathSeparator = System.getProperty("path.separator");

        // 解析依赖
        for (String path : classpath.split(pathSeparator)) {
            if (path.trim().endsWith(".jar")) {
                File jarFile = new File(path.trim());
                if (jarFile.exists() && jarFile.isFile()) {
                    jars.add(jarFile);
                }
            }
        }

        return jars;
    }

    /**
     * Maven模型解析器的简单实现
     */
    private static class DefaultModelResolver implements ModelResolver {
        @Override
        public ModelSource resolveModel(String groupId, String artifactId, String version) {
            return null;
        }

        @Override
        public ModelSource resolveModel(Dependency dependency) {
            return null;
        }

        @Override
        public ModelSource resolveModel(Parent parent) {
            return null;
        }

        @Override
        public void addRepository(Repository repository) {
            // 不做任何事
        }

        @Override
        public void addRepository(Repository repository, boolean replace) {
            // 不做任何事
        }

        @Override
        public ModelResolver newCopy() {
            return this;
        }
    }
}

