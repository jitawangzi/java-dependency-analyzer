package io.github.jitawangzi.jdepend.project;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ProjectParserFactory {
    private static final List<ProjectParser> parsers = new ArrayList<>();
    
    static {
        // 注册所有解析器，按优先级顺序
        parsers.add(new MavenProjectParser());
        parsers.add(new GradleProjectParser());
        parsers.add(new SimpleJavaProjectParser());
    }
    
    /**
     * 获取适用于给定项目的解析器
     * @param projectRoot 项目根目录
     * @return 项目解析器，如果没有找到适用的解析器则返回null
     */
    public static ProjectParser getParser(File projectRoot) {
        for (ProjectParser parser : parsers) {
            if (parser.isApplicable(projectRoot)) {
                return parser;
            }
        }
        return null;
    }
}

