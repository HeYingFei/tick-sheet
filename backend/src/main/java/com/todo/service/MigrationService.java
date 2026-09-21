package com.todo.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.todo.common.BizException;
import com.todo.config.AppProperties;
import com.todo.dto.ExportDataDTO;
import com.todo.entity.BackupRecord;
import com.todo.entity.SystemConfig;
import com.todo.entity.Tag;
import com.todo.entity.Task;
import com.todo.entity.TaskLog;
import com.todo.entity.TaskTag;
import com.todo.mapper.BackupRecordMapper;
import com.todo.mapper.TagMapper;
import com.todo.mapper.TaskLogMapper;
import com.todo.mapper.TaskMapper;
import com.todo.mapper.TaskTagMapper;
import com.todo.mapper.SystemConfigMapper;
import com.todo.support.DefaultConfig;
import com.todo.support.OperateType;
import com.todo.support.UserContext;
import com.todo.vo.BackupRecordVO;
import com.todo.vo.ImportResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据迁移服务：导出/导入/备份/恢复/清空。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MigrationService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final long MAX_IMPORT_SIZE = 10 * 1024 * 1024;
    /** 日志描述长度上限，与 task_log.operate_desc 的列宽一致 */
    private static final int DESC_MAX_LENGTH = 255;
    private static final String DEFAULT_OPERATOR = "local";
    private static final String DEFAULT_TAG_COLOR = "#3B82F6";

    private final TaskMapper taskMapper;
    private final TagMapper tagMapper;
    private final TaskTagMapper taskTagMapper;
    private final TaskLogMapper taskLogMapper;
    private final BackupRecordMapper backupRecordMapper;
    private final SystemConfigMapper systemConfigMapper;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    /**
     * 导出为 JSON 格式。
     */
    public Map<String, Object> export() {
        Long userId = UserContext.getUserId();
        List<Task> tasks = taskMapper.selectList(Wrappers.<Task>lambdaQuery()
                .eq(Task::getIsDelete, false).eq(Task::getUserId, userId));
        List<Tag> tags = tagMapper.selectList(Wrappers.<Tag>lambdaQuery()
                .eq(Tag::getIsDelete, false).eq(Tag::getUserId, userId));
        List<Long> taskIds = tasks.stream().map(Task::getId).toList();
        List<TaskTag> taskTags = taskIds.isEmpty() ? List.of() : taskTagMapper.selectByTaskIds(taskIds);
        List<TaskLog> logs = taskIds.isEmpty() ? List.of() : taskLogMapper.selectList(
                Wrappers.<TaskLog>lambdaQuery().in(TaskLog::getTaskId, taskIds)
                        .orderByDesc(TaskLog::getOperateTime));

        ExportDataDTO data = new ExportDataDTO();
        data.setExportTime(OffsetDateTime.now(ZONE).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        data.setTasks(tasks.stream().map(this::toExportTask).toList());
        data.setTags(tags.stream().map(this::toMap).toList());
        data.setTaskTags(taskTags.stream().map(this::toMap).toList());
        data.setLogs(logs.stream().map(this::logToMap).toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version", data.getVersion());
        result.put("exportTime", data.getExportTime());
        result.put("tasks", data.getTasks());
        result.put("tags", data.getTags());
        result.put("taskTags", data.getTaskTags());
        result.put("logs", data.getLogs());
        return result;
    }

    /**
     * 导入 JSON 数据。
     */
    @Transactional
    public ImportResultVO importJson(MultipartFile file) {
        if (file.isEmpty()) {
            throw BizException.paramInvalid("导入文件不能为空");
        }
        if (file.getSize() > MAX_IMPORT_SIZE) {
            throw BizException.paramInvalid("导入文件不能超过 10MB");
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(file.getInputStream(), Map.class);
            return importData(data, "导入");
        } catch (IOException e) {
            throw BizException.paramInvalid("文件解析失败: " + e.getMessage());
        }
    }

    /**
     * 创建备份文件。
     */
    @Transactional
    public BackupRecordVO backup() {
        try {
            Map<String, Object> data = export();
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);

            Path backupDir = Path.of(appProperties.getBackup().getDir());
            Files.createDirectories(backupDir);

            String fileName = "backup_" + OffsetDateTime.now(ZONE).format(
                    DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".json";
            Path filePath = backupDir.resolve(fileName);
            Files.writeString(filePath, json);

            // 统计
            @SuppressWarnings("unchecked")
            List<?> tasks = (List<?>) data.get("tasks");
            @SuppressWarnings("unchecked")
            List<?> logs = (List<?>) data.get("logs");

            BackupRecord record = new BackupRecord();
            record.setFileName(fileName);
            record.setFilePath(filePath.toAbsolutePath().toString());
            record.setFileSize(Files.size(filePath));
            record.setFileFormat("json");
            record.setScopeDesc("全量");
            record.setTaskCount(tasks != null ? tasks.size() : 0);
            record.setLogCount(logs != null ? logs.size() : 0);
            record.setUserId(UserContext.getUserId());
            backupRecordMapper.insert(record);

            return toVO(record);
        } catch (IOException e) {
            throw BizException.business("备份创建失败: " + e.getMessage());
        }
    }

    /**
     * 备份列表。
     */
    public List<BackupRecordVO> listBackups() {
        return backupRecordMapper.selectList(
                        Wrappers.<BackupRecord>lambdaQuery()
                                .eq(BackupRecord::getUserId, UserContext.getUserId())
                                .orderByDesc(BackupRecord::getCreateTime))
                .stream().map(this::toVO).toList();
    }

    /**
     * 恢复备份。
     */
    @Transactional
    public ImportResultVO restore(Long backupId) {
        BackupRecord record = getOwnedBackup(backupId);

        Path filePath = Path.of(record.getFilePath());
        if (!Files.exists(filePath)) {
            throw BizException.notFound("备份文件不存在: " + record.getFilePath());
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(Files.readString(filePath), Map.class);
            return importData(data, "恢复");
        } catch (IOException e) {
            throw BizException.business("备份恢复失败: " + e.getMessage());
        }
    }

    /**
     * 删除备份记录和文件。
     */
    @Transactional
    public void deleteBackup(Long backupId) {
        BackupRecord record = getOwnedBackup(backupId);
        try {
            Files.deleteIfExists(Path.of(record.getFilePath()));
        } catch (IOException e) {
            log.warn("备份文件删除失败: {}", record.getFilePath(), e);
        }
        backupRecordMapper.deleteById(backupId);
    }

    /**
     * 清空当前用户的业务数据。
     */
    @Transactional
    public void clearAll() {
        Long userId = UserContext.getUserId();
        // 先查出当前用户的任务 ID，再级联清理关联数据
        List<Long> taskIds = taskMapper.selectList(Wrappers.<Task>lambdaQuery()
                .select(Task::getId).eq(Task::getUserId, userId))
                .stream().map(Task::getId).toList();
        if (!taskIds.isEmpty()) {
            taskTagMapper.deleteByTaskIds(taskIds);
            taskLogMapper.delete(Wrappers.<TaskLog>lambdaQuery().in(TaskLog::getTaskId, taskIds));
        }
        taskMapper.delete(Wrappers.<Task>lambdaQuery().eq(Task::getUserId, userId));
        tagMapper.delete(Wrappers.<Tag>lambdaQuery().eq(Tag::getUserId, userId));
        // 配置删除后立即重建默认项，否则清空数据后设置页会读到空配置
        systemConfigMapper.delete(Wrappers.<SystemConfig>lambdaQuery().eq(SystemConfig::getUserId, userId));
        DefaultConfig.buildFor(userId).forEach(systemConfigMapper::insert);
    }

    // ---- 内部方法 ----

    /**
     * 按 ID 取备份并校验归属。
     *
     * <p>备份记录的 {@code file_path} 直接指向磁盘文件，若不限定归属，
     * 他人可通过猜 ID 恢复或删除本不属于自己的备份。
     * 不属于当前用户时统一按「不存在」处理，不泄露该备份 ID 是否存在。
     */
    private BackupRecord getOwnedBackup(Long backupId) {
        BackupRecord record = backupRecordMapper.selectOne(Wrappers.<BackupRecord>lambdaQuery()
                .eq(BackupRecord::getId, backupId)
                .eq(BackupRecord::getUserId, UserContext.getUserId()));
        if (record == null) {
            throw BizException.notFound("备份不存在: " + backupId);
        }
        return record;
    }

    /**
     * 把导出的数据结构写入当前用户，导入与备份恢复共用同一实现。
     *
     * <p>导出文件里的 id 是导出时的旧 ID，导入后任务与标签都会重新分配 ID，
     * 因此必须边导入边建立「旧 ID → 新 ID」映射，才能把关联表与日志挂到正确的对象上。
     *
     * <p>任务按标题、标签按名称去重，重复的不会重复创建，但仍会登记 ID 映射，
     * 使关联与日志能挂到已存在的那条记录上。
     *
     * @param actionLabel 结果文案前缀，「导入」或「恢复」
     */
    private ImportResultVO importData(Map<String, Object> data, String actionLabel) {
        Long userId = UserContext.getUserId();
        Map<Long, Long> taskIdMap = new HashMap<>();
        Map<Long, Long> tagIdMap = new HashMap<>();

        // ---- 任务：按标题去重 ----
        List<Map<String, Object>> taskMaps = getList(data, "tasks");
        Map<String, Task> taskByTitle = taskMapper.selectList(Wrappers.<Task>lambdaQuery()
                        .eq(Task::getUserId, userId)).stream()
                .filter(task -> !Boolean.TRUE.equals(task.getIsDelete()))
                .collect(Collectors.toMap(Task::getTitle, task -> task, (a, b) -> a));

        int total = taskMaps.size();
        int success = 0, duplicate = 0, fail = 0;
        for (Map<String, Object> taskMap : taskMaps) {
            String title = trimToNull(taskMap.get("title"));
            if (title == null) {
                fail++;
                continue;
            }

            Task existing = taskByTitle.get(title);
            if (existing != null) {
                duplicate++;
                rememberId(taskIdMap, taskMap.get("id"), existing.getId());
                continue;
            }

            try {
                Task task = new Task();
                task.setTitle(title);
                task.setContent(taskMap.get("content") != null ? (String) taskMap.get("content") : "");
                task.setStartTime(parseTime(taskMap.get("startTime")));
                task.setDueTime(parseTime(taskMap.get("dueTime")));
                task.setFinishTime(parseTime(taskMap.get("finishTime")));
                task.setStatus(toInt(taskMap.get("status"), 0));
                task.setIsImportant(toBool(taskMap.get("isImportant")));
                task.setIsUrgent(toBool(taskMap.get("isUrgent")));
                task.setPriority(toInt(taskMap.get("priority"), 3));
                task.setSortOrder(toInt(taskMap.get("sortOrder"), 0));
                task.setVersion(0);
                task.setIsDelete(false);
                task.setUserId(userId);
                taskMapper.insert(task);
                taskByTitle.put(title, task);
                rememberId(taskIdMap, taskMap.get("id"), task.getId());
                success++;
            } catch (Exception e) {
                log.warn("{}任务失败: {}", actionLabel, title, e);
                fail++;
            }
        }

        // ---- 标签：按名称去重 ----
        int tagCount = 0;
        Map<String, Tag> tagByName = tagMapper.selectList(Wrappers.<Tag>lambdaQuery()
                        .eq(Tag::getUserId, userId)).stream()
                .collect(Collectors.toMap(Tag::getName, tag -> tag, (a, b) -> a));

        for (Map<String, Object> tagMap : getList(data, "tags")) {
            String name = trimToNull(tagMap.get("name"));
            if (name == null) {
                continue;
            }

            Tag tag = tagByName.get(name);
            if (tag == null) {
                try {
                    tag = new Tag();
                    tag.setName(name);
                    tag.setColor(trimToNull(tagMap.get("color")) != null
                            ? (String) tagMap.get("color") : DEFAULT_TAG_COLOR);
                    tag.setUserId(userId);
                    tagMapper.insert(tag);
                    tagByName.put(name, tag);
                    tagCount++;
                } catch (Exception e) {
                    log.warn("{}标签失败: {}", actionLabel, name, e);
                    continue;
                }
            }
            rememberId(tagIdMap, tagMap.get("id"), tag.getId());
        }

        int linkCount = importTaskTags(getList(data, "taskTags"), taskIdMap, tagIdMap);
        int logCount = importLogs(getList(data, "logs"), taskIdMap, userId);

        ImportResultVO result = new ImportResultVO();
        result.setTotalRows(total);
        result.setSuccessCount(success);
        result.setDuplicateCount(duplicate);
        result.setFailCount(fail);
        result.setMessage(String.format(
                "%s完成：任务 成功 %d / 重复跳过 %d / 失败 %d，标签 %d，关联 %d，日志 %d",
                actionLabel, success, duplicate, fail, tagCount, linkCount, logCount));
        return result;
    }

    /**
     * 恢复任务标签关联。
     *
     * <p>task_tag 是复合主键表，直接插入会与已有记录撞主键，
     * 因此先按任务批量查出已有标签，只补差集。
     */
    private int importTaskTags(List<Map<String, Object>> links,
                               Map<Long, Long> taskIdMap, Map<Long, Long> tagIdMap) {
        Map<Long, Set<Long>> pending = new LinkedHashMap<>();
        for (Map<String, Object> link : links) {
            Long taskId = taskIdMap.get(toLong(link.get("taskId")));
            Long tagId = tagIdMap.get(toLong(link.get("tagId")));
            if (taskId == null || tagId == null) {
                continue;
            }
            pending.computeIfAbsent(taskId, key -> new LinkedHashSet<>()).add(tagId);
        }
        if (pending.isEmpty()) {
            return 0;
        }

        Map<Long, Set<Long>> owned = taskTagMapper.selectByTaskIds(pending.keySet()).stream()
                .collect(Collectors.groupingBy(TaskTag::getTaskId,
                        Collectors.mapping(TaskTag::getTagId, Collectors.toSet())));

        int inserted = 0;
        for (Map.Entry<Long, Set<Long>> entry : pending.entrySet()) {
            Set<Long> existing = owned.getOrDefault(entry.getKey(), Set.of());
            List<Long> missing = entry.getValue().stream()
                    .filter(tagId -> !existing.contains(tagId))
                    .toList();
            if (!missing.isEmpty()) {
                taskTagMapper.insertBatch(entry.getKey(), missing);
                inserted += missing.size();
            }
        }
        return inserted;
    }

    /**
     * 恢复操作日志。
     *
     * <p>task_log 没有业务唯一键，若不去重，反复导入同一份备份会让时间线成倍增长。
     * 这里以「任务 + 操作类型 + 操作时间」作为去重键。
     */
    private int importLogs(List<Map<String, Object>> logs, Map<Long, Long> taskIdMap, Long userId) {
        if (logs.isEmpty() || taskIdMap.isEmpty()) {
            return 0;
        }

        Set<String> existingKeys = taskLogMapper.selectList(Wrappers.<TaskLog>lambdaQuery()
                        .eq(TaskLog::getUserId, userId)
                        .in(TaskLog::getTaskId, taskIdMap.values())).stream()
                .map(this::logKey)
                .collect(Collectors.toSet());

        int inserted = 0;
        for (Map<String, Object> logMap : logs) {
            Long taskId = taskIdMap.get(toLong(logMap.get("taskId")));
            OffsetDateTime operateTime = parseTime(logMap.get("operateTime"));
            int operateType = toInt(logMap.get("operateType"), 0);
            // operate_type 有 1~7 的 CHECK 约束，范围外的记录直接跳过
            if (taskId == null || operateTime == null
                    || operateType < OperateType.CREATE || operateType > OperateType.RESTORE) {
                continue;
            }
            if (!existingKeys.add(taskId + "|" + operateType + "|" + operateTime)) {
                continue;
            }

            try {
                String operator = trimToNull(logMap.get("operator"));
                TaskLog taskLog = new TaskLog();
                taskLog.setTaskId(taskId);
                taskLog.setOperateType(operateType);
                taskLog.setOperateDesc(truncateDesc(trimToNull(logMap.get("operateDesc"))));
                taskLog.setOperateDetail(trimToNull(logMap.get("operateDetail")));
                taskLog.setOperator(operator != null ? operator : DEFAULT_OPERATOR);
                taskLog.setUserId(userId);
                taskLog.setOperateTime(operateTime);
                taskLogMapper.insertLog(taskLog);
                inserted++;
            } catch (Exception e) {
                log.warn("{}日志失败: taskId={}", logMap.get("taskId"), e);
            }
        }
        return inserted;
    }

    private String logKey(TaskLog taskLog) {
        return taskLog.getTaskId() + "|" + taskLog.getOperateType() + "|" + taskLog.getOperateTime();
    }

    /** 登记「导出时的旧 ID → 导入后的新 ID」，旧 ID 缺失或非法时不登记 */
    private void rememberId(Map<Long, Long> idMap, Object oldId, Long newId) {
        Long from = toLong(oldId);
        if (from != null) {
            idMap.put(from, newId);
        }
    }

    private String truncateDesc(String desc) {
        if (desc == null) {
            return "";
        }
        return desc.length() <= DESC_MAX_LENGTH ? desc : desc.substring(0, DESC_MAX_LENGTH);
    }

    private String trimToNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private ExportDataDTO.TaskExportDTO toExportTask(Task task) {
        ExportDataDTO.TaskExportDTO dto = new ExportDataDTO.TaskExportDTO();
        dto.setId(task.getId());
        dto.setTitle(task.getTitle());
        dto.setContent(task.getContent());
        dto.setStartTime(task.getStartTime() != null ? task.getStartTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : null);
        dto.setDueTime(task.getDueTime() != null ? task.getDueTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : null);
        dto.setFinishTime(task.getFinishTime() != null ? task.getFinishTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : null);
        dto.setStatus(task.getStatus());
        dto.setIsImportant(task.getIsImportant());
        dto.setIsUrgent(task.getIsUrgent());
        dto.setPriority(task.getPriority());
        dto.setSortOrder(task.getSortOrder());
        dto.setCreateTime(task.getCreateTime() != null ? task.getCreateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : null);
        dto.setUpdateTime(task.getUpdateTime() != null ? task.getUpdateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : null);
        return dto;
    }

    private Map<String, Object> toMap(Tag tag) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", tag.getId());
        m.put("name", tag.getName());
        m.put("color", tag.getColor());
        return m;
    }

    private Map<String, Object> toMap(TaskTag tt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("taskId", tt.getTaskId());
        m.put("tagId", tt.getTagId());
        return m;
    }

    private Map<String, Object> logToMap(TaskLog log) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("taskId", log.getTaskId());
        m.put("operateType", log.getOperateType());
        m.put("operateDesc", log.getOperateDesc());
        m.put("operateDetail", log.getOperateDetail());
        m.put("operator", log.getOperator());
        m.put("operateTime", log.getOperateTime() != null ? log.getOperateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : null);
        return m;
    }

    private BackupRecordVO toVO(BackupRecord record) {
        BackupRecordVO vo = new BackupRecordVO();
        vo.setId(record.getId());
        vo.setFileName(record.getFileName());
        vo.setFileSize(record.getFileSize());
        vo.setFileFormat(record.getFileFormat());
        vo.setScopeDesc(record.getScopeDesc());
        vo.setTaskCount(record.getTaskCount());
        vo.setLogCount(record.getLogCount());
        vo.setCreateTime(record.getCreateTime());
        return vo;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getList(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(item -> item instanceof Map)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }
        return List.of();
    }

    private OffsetDateTime parseTime(Object value) {
        if (value == null) return null;
        try {
            return OffsetDateTime.parse((String) value);
        } catch (Exception e) {
            return null;
        }
    }

    private int toInt(Object value, int defaultVal) {
        if (value == null) return defaultVal;
        if (value instanceof Number n) return n.intValue();
        try { return Integer.parseInt((String) value); } catch (Exception e) { return defaultVal; }
    }

    private boolean toBool(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
