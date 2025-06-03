package io.github.jitawangzi.jdepend.config;

import java.util.Set;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;

/**
 * 导入配置接口，使用Owner框架定义需要保留和跳过的导入包
 */
@Sources("classpath:application.properties")
public interface ImportConfig extends Config {
	// 单例实例，可以直接在应用中使用
	static ImportConfig INSTANCE = ConfigFactory.create(ImportConfig.class);

	/**
	 * 是否启用导入跳过功能
	 */
	@DefaultValue("false")
	@Key("import.skip.enabled")
	boolean isSkipEnabled();

	/**
	 * 获取需要跳过的包前缀集合
	 */
	@DefaultValue("java.lang,java.util,java.io,cn.game")
	@Separator(",")
	@Key("import.skip.prefixes")
	Set<String> getSkipPrefixes();

	/**
	 * 获取需要保留的包前缀集合
	 */
	@DefaultValue("io.vertx,org.apache,org.slf4j,org.springframework,com.google,javax.,jakarta.,lombok")
	@Separator(",")
	@Key("import.keep.prefixes")
	Set<String> getKeepPrefixes();
}