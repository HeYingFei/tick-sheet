package com.todo.vo;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 任务视图对象。
 *
 * <p>其中 {@code overdue} 与 {@code overdueDays} 是查询时派生的计算字段，
 * 数据库中并不存在对应列，见设计方案 §4.2。
 */
@Data
public class TaskVO {

    private Long id;

    private String title;

    private String content;

    private OffsetDateTime startTime;

    private OffsetDateTime dueTime;

    private OffsetDateTime finishTime;

    /** 0-待办 1-进行中 2-已完成 3-已取消 */
    private Integer status;

    private String statusText;

    private Boolean isImportant;

    private Boolean isUrgent;

    /** 象限 1-4，由 isImportant/isUrgent 派生 */
    private Integer quadrant;

    private String quadrantName;

    /** 1-极高 2-高 3-中 4-低 */
    private Integer priority;

    private Integer sortOrder;

    private List<TagBriefVO> tags;

    // ---- 派生字段 ----

    /** 是否逾期中：未结束且已过截止时间 */
    private Boolean overdue;

    /** 逾期天数，未逾期为 0 */
    private Long overdueDays;

    /**
     * 象限变更后给出的建议优先级，见设计方案 §4.3。
     * 仅在象限变更接口的响应中返回，前端据此二次确认，后端不静默改库。
     */
    private Integer suggestPriority;

    // ---- 元信息 ----

    private Integer version;

    private OffsetDateTime createTime;

    private OffsetDateTime updateTime;
}
