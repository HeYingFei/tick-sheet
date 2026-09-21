package com.todo.vo;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 时间线单条日志视图。
 */
@Data
public class TimelineLogVO {

    private Long id;

    private Long taskId;

    /** 任务标题。日志记录的是当时快照，标题修改后不回溯 */
    private String taskTitle;

    private Integer operateType;

    private String operateTypeText;

    private String operateDesc;

    private JsonNode operateDetail;

    private String operator;

    private OffsetDateTime operateTime;
}
