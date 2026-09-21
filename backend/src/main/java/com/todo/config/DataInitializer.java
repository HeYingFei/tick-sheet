package com.todo.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.todo.entity.BackupRecord;
import com.todo.entity.SysUser;
import com.todo.entity.SystemConfig;
import com.todo.entity.Tag;
import com.todo.entity.Task;
import com.todo.entity.TaskLog;
import com.todo.mapper.BackupRecordMapper;
import com.todo.mapper.SysUserMapper;
import com.todo.mapper.SystemConfigMapper;
import com.todo.mapper.TagMapper;
import com.todo.mapper.TaskLogMapper;
import com.todo.mapper.TaskMapper;
import com.todo.support.DefaultConfig;
import com.todo.support.PasswordUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 应用启动时初始化默认数据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final SysUserMapper sysUserMapper;
    private final SystemConfigMapper systemConfigMapper;
    private final TaskMapper taskMapper;
    private final TagMapper tagMapper;
    private final TaskLogMapper taskLogMapper;
    private final BackupRecordMapper backupRecordMapper;

    @Override
    public void run(String... args) {
        // 顺序不可颠倒：必须先把 user_id = 0 的历史数据归入账号，再补默认配置。
        // 反过来的话，新插入的默认配置会与随后迁移过来的记录撞 system_config 的
        // (user_id, config_key) 唯一索引，直接导致应用启动失败。
        SysUser admin = ensureAdmin();
        migrateOrphanData(admin.getId());
        ensureDefaultConfig(admin.getId());
    }

    /** 保证存在一个账号；一个都没有时创建默认管理员 */
    private SysUser ensureAdmin() {
        List<SysUser> users = sysUserMapper.selectList(null);
        if (!users.isEmpty()) {
            return users.get(0);
        }

        SysUser admin = new SysUser();
        admin.setUsername("admin");
        admin.setPasswordHash(PasswordUtil.hash("admin123"));
        admin.setNickname("管理员");
        admin.setAvatarUrl("");
        admin.setStatus(1);
        sysUserMapper.insert(admin);
        log.info("已创建默认管理员账号: admin / admin123，请尽快修改密码");
        return admin;
    }

    /** 补齐缺失的默认配置项，已存在的不覆盖，保留用户改过的值 */
    private void ensureDefaultConfig(Long userId) {
        Set<String> existingKeys = systemConfigMapper.selectList(
                        Wrappers.<SystemConfig>lambdaQuery().eq(SystemConfig::getUserId, userId))
                .stream().map(SystemConfig::getConfigKey).collect(Collectors.toSet());

        DefaultConfig.buildFor(userId).stream()
                .filter(config -> !existingKeys.contains(config.getConfigKey()))
                .forEach(systemConfigMapper::insert);
    }

    /**
     * V2 迁移后业务表的 user_id 全部是 0（列默认值），需要归入第一个账号。
     */
    private void migrateOrphanData(Long adminId) {
        int migrated = 0;
        migrated += migrateOrphanConfigs(adminId);
        migrated += migrateTable("task", taskMapper.selectList(
                Wrappers.<Task>lambdaQuery().eq(Task::getUserId, 0L)),
                task -> { task.setUserId(adminId); taskMapper.updateById(task); });
        migrated += migrateOrphanTags(adminId);
        migrated += migrateTable("task_log", taskLogMapper.selectList(
                Wrappers.<TaskLog>lambdaQuery().eq(TaskLog::getUserId, 0L)),
                log_ -> { log_.setUserId(adminId); taskLogMapper.updateById(log_); });
        migrated += migrateTable("backup_record", backupRecordMapper.selectList(
                Wrappers.<BackupRecord>lambdaQuery().eq(BackupRecord::getUserId, 0L)),
                rec -> { rec.setUserId(adminId); backupRecordMapper.updateById(rec); });

        if (migrated > 0) {
            log.info("已将 {} 条孤立数据归入账号 {}", migrated, adminId);
        }
    }

    /**
     * 迁移 user_id = 0 的配置。
     *
     * <p>system_config 上有 (user_id, config_key) 唯一索引，若目标账号已有同键记录，
     * 直接改挂 user_id 会撞唯一索引。此时说明同键配置已经就位，残留记录直接丢弃。
     */
    private int migrateOrphanConfigs(Long adminId) {
        List<SystemConfig> orphans = systemConfigMapper.selectList(
                Wrappers.<SystemConfig>lambdaQuery().eq(SystemConfig::getUserId, 0L));
        if (orphans.isEmpty()) {
            return 0;
        }

        Set<String> ownedKeys = systemConfigMapper.selectList(
                        Wrappers.<SystemConfig>lambdaQuery().eq(SystemConfig::getUserId, adminId))
                .stream().map(SystemConfig::getConfigKey).collect(Collectors.toCollection(HashSet::new));

        for (SystemConfig config : orphans) {
            if (ownedKeys.add(config.getConfigKey())) {
                config.setUserId(adminId);
                systemConfigMapper.updateById(config);
            } else {
                systemConfigMapper.deleteById(config.getId());
            }
        }
        log.info("  迁移 system_config 共 {} 条", orphans.size());
        return orphans.size();
    }

    /**
     * 迁移 user_id = 0 的标签。
     *
     * <p>tag 上有 (user_id, name) 唯一索引（未删除范围内），冲突处理同配置表。
     * 丢弃走逻辑删除，残留记录不再占用该唯一索引。
     */
    private int migrateOrphanTags(Long adminId) {
        List<Tag> orphans = tagMapper.selectList(Wrappers.<Tag>lambdaQuery().eq(Tag::getUserId, 0L));
        if (orphans.isEmpty()) {
            return 0;
        }

        Set<String> ownedNames = tagMapper.selectList(
                        Wrappers.<Tag>lambdaQuery().eq(Tag::getUserId, adminId))
                .stream().map(Tag::getName).collect(Collectors.toCollection(HashSet::new));

        for (Tag tag : orphans) {
            if (ownedNames.add(tag.getName())) {
                tag.setUserId(adminId);
                tagMapper.updateById(tag);
            } else {
                tagMapper.deleteById(tag.getId());
            }
        }
        log.info("  迁移 tag 共 {} 条", orphans.size());
        return orphans.size();
    }

    @FunctionalInterface
    private interface Setter<T> { void set(T entity); }

    private <T> int migrateTable(String tableName, List<T> items, Setter<T> setter) {
        for (T item : items) {
            setter.set(item);
        }
        if (!items.isEmpty()) {
            log.info("  迁移 {} 共 {} 条", tableName, items.size());
        }
        return items.size();
    }
}
