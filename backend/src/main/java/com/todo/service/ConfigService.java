package com.todo.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.todo.common.BizException;
import com.todo.entity.SystemConfig;
import com.todo.mapper.SystemConfigMapper;
import com.todo.support.DefaultConfig;
import com.todo.support.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统配置服务，见设计方案 §3.7。
 */
@Service
@RequiredArgsConstructor
public class ConfigService {

    private final SystemConfigMapper systemConfigMapper;

    /**
     * 返回全部配置，顺序与 {@link DefaultConfig#ENTRIES} 一致。
     *
     * <p>以代码中的默认项定义为骨架，再覆盖该用户库里的值。这样后加的配置项对老用户也能
     * 立刻读到默认值，不必依赖启动时的补键逻辑，也不会因为缺少行而从前端消失。
     */
    public Map<String, String> getAll() {
        Map<String, String> stored = systemConfigMapper.selectList(Wrappers.<SystemConfig>lambdaQuery()
                        .eq(SystemConfig::getUserId, UserContext.getUserId()))
                .stream().collect(Collectors.toMap(
                        SystemConfig::getConfigKey,
                        SystemConfig::getConfigValue,
                        (a, b) -> b));

        Map<String, String> result = new LinkedHashMap<>();
        for (DefaultConfig.Entry entry : DefaultConfig.ENTRIES) {
            result.put(entry.key(), stored.getOrDefault(entry.key(), entry.value()));
        }
        return result;
    }

    public String get(String key, String defaultValue) {
        return getAll().getOrDefault(key, defaultValue);
    }

    /**
     * 批量更新配置。
     *
     * <p>白名单以 {@link DefaultConfig#ENTRIES} 为准，而不是库里现有的行：
     * 若以现有行为准，用户还没有的新增配置项会被误判为「不支持」。
     *
     * <p>用户缺少对应行时按默认定义补齐后写入，因此新增配置项对存量用户同样可保存。
     */
    @Transactional
    public Map<String, String> updateAll(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            throw BizException.paramInvalid("配置内容不能为空");
        }

        Long userId = UserContext.getUserId();
        Map<String, DefaultConfig.Entry> supported = DefaultConfig.ENTRIES.stream()
                .collect(Collectors.toMap(DefaultConfig.Entry::key, entry -> entry));

        List<String> unknown = values.keySet().stream()
                .filter(key -> !supported.containsKey(key))
                .sorted()
                .toList();
        if (!unknown.isEmpty()) {
            throw BizException.paramInvalid("不支持的配置项: " + String.join(", ", unknown));
        }

        Map<String, SystemConfig> existing = systemConfigMapper.selectList(
                        Wrappers.<SystemConfig>lambdaQuery().eq(SystemConfig::getUserId, userId))
                .stream().collect(Collectors.toMap(
                        SystemConfig::getConfigKey, config -> config, (a, b) -> a));

        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey();
            String newValue = entry.getValue();
            if (newValue == null) {
                continue;
            }

            SystemConfig config = existing.get(key);
            if (config == null) {
                DefaultConfig.Entry definition = supported.get(key);
                SystemConfig created = new SystemConfig();
                created.setConfigKey(key);
                created.setConfigValue(newValue);
                created.setConfigDesc(definition.desc());
                created.setUserId(userId);
                systemConfigMapper.insert(created);
            } else if (!newValue.equals(config.getConfigValue())) {
                config.setConfigValue(newValue);
                systemConfigMapper.updateById(config);
            }
        }
        return getAll();
    }
}
