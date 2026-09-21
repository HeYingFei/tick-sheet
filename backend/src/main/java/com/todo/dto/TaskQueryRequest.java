package com.todo.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 任务分页查询条件，见设计方案 §6.3。
 *
 * <p>多值参数用逗号分隔，如 {@code status=0,1}、{@code tagIds=3,7}。
 * Spring 的 StringToCollectionConverter 会自动按逗号切分。
 */
@Data
public class TaskQueryRequest {

    /** 关键词，匹配标题或备注 */
    private String keyword;

    /** 状态多选：0-待办 1-进行中 2-已完成 3-已取消 */
    private List<Integer> status;

    /** 象限：1-4，见设计方案 §3.3 */
    private Integer quadrant;

    /** 标签多选，语义为 AND（同时包含所选全部标签），见设计方案 §4.4 */
    private List<Long> tagIds;

    /** 时间范围，左闭右开 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime startTime;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime endTime;

    /**
     * 时间范围作用的字段：create_time | due_time | start_time | finish_time。
     * 默认 create_time。
     */
    private String timeField;

    /** 只看逾期任务，条件为 status IN (0,1) AND due_time &lt; now() */
    private Boolean overdueOnly;

    /** 排序字段白名单：createTime | updateTime | dueTime | priority | sortOrder */
    private String sortBy;

    /** asc | desc */
    private String sortOrder;

    private Integer page = 1;

    private Integer size = 20;
}
