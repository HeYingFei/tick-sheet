package com.todo.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.todo.common.BizException;
import com.todo.entity.SystemConfig;
import com.todo.mapper.SystemConfigMapper;
import com.todo.support.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 系统配置服务，见设计方案 §3.7。
 */
@Service
@RequiredArgsConstructor
public class ConfigService {

    private final SystemConfigMapper systemConfigMapper;

    /** 返回全部配置，保持插入顺序 */
    public Map<String, String> getAll() {
        Long userId = UserContext.getUserId();
        return systemConfigMapper.selectList(Wrappers.<SystemConfig>lambdaQuery()
                        .eq(SystemConfig::getUserId, userId))
                .stream().collect(Collectors.toMap(
                SystemConfig::getConfigKey,
                SystemConfig::getConfigValue,
                (a, b) -> b,
                LinkedHashMap::new));
    }

    public String get(String key, String defaultValue) {
        return getAll().getOrDefault(key, defaultValue);
    }

    /**
     * 批量更新配置。
     *
     * <p>只接受已存在的配置键：配置文件是白名单，避免通过接口写入任意键值。
     */
    @Transactional
    public Map<String, String> updateAll(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            throw BizException.paramInvalid("配置内容不能为空");
        }

        List<SystemConfig> existing = systemConfigMapper.selectList(
                Wrappers.<SystemConfig>lambdaQuery().eq(SystemConfig::getUserId, UserContext.getUserId()));
        Set<String> knownKeys = existing.stream()
                .map(SystemConfig::getConfigKey)
                .collect(Collectors.toSet());

        List<String> unknown = values.keySet().stream()
                .filter(key -> !knownKeys.contains(key))
                .sorted()
                .toList();
        if (!unknown.isEmpty()) {
            throw BizException.paramInvalid("不支持的配置项: " + String.join(", ", unknown));
        }

        for (SystemConfig config : existing) {
            String newValue = values.get(config.getConfigKey());
            if (newValue != null && !newValue.equals(config.getConfigValue())) {
                config.setConfigValue(newValue);
                systemConfigMapper.updateById(config);
            }
        }
        return getAll();
    }
}
