package com.todo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.todo.common.PageResult;
import com.todo.entity.Task;
import com.todo.entity.TaskLog;
import com.todo.mapper.TaskLogMapper;
import com.todo.mapper.TaskMapper;
import com.todo.support.OperateType;
import com.todo.support.UserContext;
import com.todo.vo.TimelineLogVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 时间线服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TimelineService {

    private final TaskLogMapper taskLogMapper;
    private final TaskMapper taskMapper;
    private final ObjectMapper objectMapper;

    /**
     * 分页查询操作日志。
     *
     * @param startTime       起始时间（含），可为 null
     * @param endTime         结束时间（不含），可为 null
     * @param operateTypes    操作类型筛选，可为 null
     * @param includeDeleted  是否包含已删除任务的日志
     * @param page            页码
     * @param size            每页大小
     */
    public PageResult<TimelineLogVO> logs(OffsetDateTime startTime, OffsetDateTime endTime,
                                          List<Integer> operateTypes, boolean includeDeleted,
                                          Integer page, Integer size) {
        long current = page == null || page < 1 ? 1 : page;
        long pageSize = size == null || size < 1 ? 20 : Math.min(size, 200);

        LambdaQueryWrapper<TaskLog> wrapper = Wrappers.<TaskLog>lambdaQuery();
        wrapper.eq(TaskLog::getUserId, UserContext.getUserId());

        if (startTime != null) {
            wrapper.ge(TaskLog::getOperateTime, startTime);
        }
        if (endTime != null) {
            wrapper.lt(TaskLog::getOperateTime, endTime);
        }
        if (operateTypes != null && !operateTypes.isEmpty()) {
            List<Integer> types = operateTypes.stream().filter(Objects::nonNull).distinct().toList();
            if (!types.isEmpty()) {
                wrapper.in(TaskLog::getOperateType, types);
            }
        }

        // task_log 刻意不设外键，软删任务的日志仍留在表中。
        // 过滤必须下推到 SQL：若放到内存里做，total 仍按未过滤的结果统计，
        // 会出现「显示 16 条却告诉前端共 40 条」的分页偏差。
        if (!includeDeleted) {
            wrapper.notInSql(TaskLog::getTaskId, "SELECT id FROM task WHERE is_delete = TRUE");
        }

        wrapper.orderByDesc(TaskLog::getOperateTime);

        Page<TaskLog> result = taskLogMapper.selectPage(new Page<>(current, pageSize), wrapper);

        // 批量加载任务标题，查不到说明任务已被软删，标题降级展示
        Set<Long> taskIds = result.getRecords().stream()
                .map(TaskLog::getTaskId)
                .collect(Collectors.toSet());

        Map<Long, String> titlesByTask = Map.of();
        if (!taskIds.isEmpty()) {
            List<Task> tasks = taskMapper.selectBatchIds(taskIds);
            titlesByTask = tasks.stream()
                    .collect(Collectors.toMap(Task::getId, Task::getTitle, (a, b) -> a));
        }

        Map<Long, String> finalTitles = titlesByTask;
        List<TimelineLogVO> logs = result.getRecords().stream()
                .map(entry -> toVO(entry, finalTitles.getOrDefault(entry.getTaskId(), "[已删除]")))
                .toList();

        return PageResult.of(result, logs);
    }

    private TimelineLogVO toVO(TaskLog taskLog, String taskTitle) {
        TimelineLogVO vo = new TimelineLogVO();
        vo.setId(taskLog.getId());
        vo.setTaskId(taskLog.getTaskId());
        vo.setTaskTitle(taskTitle);
        vo.setOperateType(taskLog.getOperateType());
        vo.setOperateTypeText(OperateType.text(taskLog.getOperateType()));
        vo.setOperateDesc(taskLog.getOperateDesc());
        vo.setOperator(taskLog.getOperator());
        vo.setOperateTime(taskLog.getOperateTime());

        if (taskLog.getOperateDetail() != null && !taskLog.getOperateDetail().isEmpty()) {
            try {
                vo.setOperateDetail(objectMapper.readTree(taskLog.getOperateDetail()));
            } catch (Exception e) {
                log.warn("操作日志明细反序列化失败，已降级为 null: logId={}", taskLog.getId(), e);
            }
        }
        return vo;
    }
}
