package io.github.jitawangzi.jdepend.config;

import java.util.List;
import java.util.Set;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;

/**
 * 配置接口，使用Owner框架定义分析器的各种配置参数
 * 优先级：系统属性 > application.properties文件
 */
@Sources({"system:properties", "classpath:application.properties"})
//直接运行似乎只能使用 classpath:application.properties
//@Sources({ "classpath:application.properties" })
public interface AppConfig extends Config {
	// 单例实例，可以直接在应用中使用
//	public static AppConfig INSTANCE = ConfigFactory.create(AppConfig.class);

	/**
	 * 获取项目根目录路径字符串
	 */
	@Key("project.root")
	String getProjectRootPath();

	/**
	 * 获取主类名称
	 */
	@Key("main.class")
	String getMainClass();

	/**
	 * 获取类的引用分析深度
	 */
	@DefaultValue("-1")
	@Key("max.depth")
	int getMaxDepth();

	@Key("method.body.max.depth")
	int getMethodBodyMaxDepth();

	@Key("show.error.stacktrace")
	boolean showErrorStacktrace();

	/**
	 * 是否简化方法体
	 */
	@DefaultValue("true")
	@Key("simplify.methods")
	boolean isSimplifyMethods();

	/**
	 * 获取输出文件名
	 */
	@DefaultValue("output.md")
	@Key("output.file")
	String getOutputFile();

	/**
	 * 获取排除的包前缀集合
	 */
	@Separator(",")
	@Key("excluded.packages")
	Set<String> getExcludedPackages();

	/**
	 * 获取项目包前缀
	 */
	@Separator(",")
	@Key("project.package.prefixes")
	Set<String> getProjectPackagePrefixes();

	/**
	 * 获取需要保留方法体的特例类集合
	 */
	@Separator(",")
	@Key("method.exceptions")
	Set<String> getMethodExceptions();

	/**
	 * 获取内容大小阈值
	 */
	@DefaultValue("100000")
	@Key("content.size.threshold")
	int getContentSizeThreshold();

	/**
	 * 是否省略JavaBean的getter和setter方法
	 */
	@DefaultValue("true")
	@Key("omit.bean.methods")
	boolean isOmitBeanMethods();

	/**
	 * 是否在输出中显示被省略的访问器方法签名
	 */
	@DefaultValue("true")
	@Key("show.omitted.accessors")
	boolean isShowOmittedAccessors();

	/**
	 * 是否只保留被引用的方法
	 */
	@DefaultValue("true")
	@Key("keep.only.referenced.methods")
	boolean isKeepOnlyReferencedMethods();

	/**
	 * 是否显示被移除的方法
	 */
	@DefaultValue("true")
	@Key("show.removed.methods")
	boolean isShowRemovedMethods();

	/**
	 * 是否启用目录模式
	 */
	@DefaultValue("false")
	@Key("directory.mode.enabled")
	boolean isDirectoryModeEnabled();

	/**
	 * 获取目录路径
	 */
	@Key("directory.path")
	String getDirectoryPath();

	/**
	 * 获取包含文件模式
	 */
	@Separator(",")
	@Key("directory.include.files")
	List<String> getIncludeFiles();

	/**
	 * 获取排除文件模式
	 */
	@Separator(",")
	@Key("directory.exclude.files")
	List<String> getExcludeFiles();

	/**
	 * 获取包含文件夹
	 */
	@Separator(",")
	@Key("directory.include.folders")
	Set<String> getIncludeFolders();

	/**
	 * 获取排除文件夹
	 */
	@Separator(",")
	@Key("directory.exclude.folders")
	Set<String> getExcludeFolders();

	/**
	 * 获取允许的文件扩展名
	 */
	@Separator(",")
	@Key("directory.allowed.extensions")
	Set<String> getAllowedFileExtensions();

	/**
	 * 获取额外的源码目录
	 */
	@Separator(",")
	@Key("source.directories")
	List<String> getSourceDirectories();
}

