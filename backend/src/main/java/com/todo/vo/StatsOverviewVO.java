package com.todo.vo;

import lombok.Data;

/**
 * 仪表盘概览数据，见设计方案 §4.7。
 */
@Data
public class StatsOverviewVO {

    private Long totalTasks;

    private Long todoCount;

    private Long doingCount;

    private Long doneCount;

    private Long canceledCount;

    /** 逾期中：status IN (0,1) AND due_time < now() */
    private Long overdueCount;

    /** 完成率。分母为 0 时返回 null */
    private Double completionRate;

    /** 逾期率。分母为 0 时返回 null */
    private Double overdueRate;

    /** 今日新增任务数 */
    private Long todayNewCount;

    /** 今日完成任务数 */
    private Long todayDoneCount;
}
