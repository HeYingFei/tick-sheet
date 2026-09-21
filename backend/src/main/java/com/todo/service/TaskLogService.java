package com.todo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.todo.entity.TaskLog;
import com.todo.mapper.TaskLogMapper;
import com.todo.support.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 任务操作日志记录，时间线视图的数据来源，见设计方案 §5.5。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskLogService {

    private static final int DESC_MAX_LENGTH = 255;
    private static final String DEFAULT_OPERATOR = "local";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final TaskLogMapper taskLogMapper;
    private final ObjectMapper objectMapper;

    /**
     * 记录一条操作日志。
     *
     * @param detail 变更快照，形如 {"due_time":{"old":x,"new":y}}，可为 null
     */
    public void record(Long taskId, int operateType, String desc, Object detail) {
        TaskLog taskLog = new TaskLog();
        taskLog.setTaskId(taskId);
        taskLog.setOperateType(operateType);
        taskLog.setOperateDesc(truncate(desc));
        taskLog.setOperateDetail(toJson(detail));
        taskLog.setOperator(DEFAULT_OPERATOR);
        taskLog.setUserId(UserContext.getUserId());
        taskLog.setOperateTime(OffsetDateTime.now(ZONE));
        taskLogMapper.insertLog(taskLog);
    }

    private String truncate(String desc) {
        if (desc == null) {
            return "";
        }
        return desc.length() <= DESC_MAX_LENGTH ? desc : desc.substring(0, DESC_MAX_LENGTH);
    }

    private String toJson(Object detail) {
        if (detail == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            // 快照序列化失败不应阻断主流程
            log.warn("操作日志明细序列化失败，已降级为 null", e);
            return null;
        }
    }
}
