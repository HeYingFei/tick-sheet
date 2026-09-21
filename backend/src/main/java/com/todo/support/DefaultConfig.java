package com.todo.support;

import com.todo.entity.SystemConfig;

import java.util.List;

/**
 * 系统配置的默认项定义，见设计方案 §5.6。
 *
 * <p>管理员初始化、新用户注册、数据清空后重建三处共用同一份定义，避免默认配置
 * 出现多份副本后彼此不一致。描述文案与 V1 建表脚本中的初始化数据保持一致。
 */
public final class DefaultConfig {

    /** 单个配置项：键名、默认值、描述 */
    public record Entry(String key, String value, String desc) {
    }

    public static final List<Entry> ENTRIES = List.of(
            new Entry("theme_mode", "light", "系统主题：light-浅色，dark-深色"),
            new Entry("default_priority", "3", "默认任务优先级：1-极高，2-高，3-中，4-低"),
            new Entry("time_format", "YYYY-MM-DD HH:mm", "时间展示格式，使用 dayjs 记号"),
            new Entry("week_start", "1", "周起始日：1-周一，7-周日"),
            new Entry("calendar_field", "due_time", "日历视图映射字段：due_time-截止时间，start_time-开始时间")
    );

    private DefaultConfig() {
    }

    /** 构造指定用户的全部默认配置实体 */
    public static List<SystemConfig> buildFor(Long userId) {
        return ENTRIES.stream().map(entry -> {
            SystemConfig config = new SystemConfig();
            config.setConfigKey(entry.key());
            config.setConfigValue(entry.value());
            config.setConfigDesc(entry.desc());
            config.setUserId(userId);
            return config;
        }).toList();
    }
}
