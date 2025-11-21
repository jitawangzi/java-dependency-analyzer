package io.github.jitawangzi.jdepend.config;

import org.aeonbits.owner.ConfigFactory;
import java.util.Properties;

public class AppConfigManager {

    // 这里的 instance 不是 final 的，可以被修改
    private static volatile AppConfig instance;

    // 私有构造，防止实例化
    private AppConfigManager() {}

    /**
     * 获取当前配置实例（懒加载）
     */
    public static AppConfig get() {
        if (instance == null) {
            synchronized (AppConfigManager.class) {
                if (instance == null) {
                    // 默认初始化
                    instance = ConfigFactory.create(AppConfig.class);
                }
            }
        }
        return instance;
    }

    /**
     * 重新加载配置（用于 Eclipse 插件）
     * @param customProperties 来自 Eclipse 界面设置的属性
     */
    public static void reload(Properties customProperties) {
        // Owner 允许传入 Properties，它的优先级最高，会覆盖 @Sources 中的配置
        // 这样你就可以把 Eclipse 的 PreferenceStore 转成 Properties 传进来
        instance = ConfigFactory.create(AppConfig.class, customProperties);
    }
    
    /**
     * 重置为默认配置
     */
    public static void reset() {
        instance = ConfigFactory.create(AppConfig.class);
    }
}