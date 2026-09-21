package com.todo.vo;

import lombok.Data;

/**
 * 完成率/逾期率统计，见设计方案 §4.7。
 */
@Data
public class CompletionStatsVO {

    /** 统计周期内的任务基数（分母） */
    private Long base;

    private Long doneCount;

    private Long overdueCount;

    /** base=0 时返回 null */
    private Double completionRate;

    /** base=0 时返回 null */
    private Double overdueRate;

    private Long newCount;

    /** 统计周期天数 */
    private Long days;

    /** 新增均值 = newCount / days */
    private Double avgNewPerDay;

    private Long importantCount;

    /** 核心任务占比 = importantCount / base。base=0 时为 null */
    private Double importantRate;
}
