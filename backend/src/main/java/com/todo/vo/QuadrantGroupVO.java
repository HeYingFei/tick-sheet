package com.todo.vo;

import lombok.Data;

import java.util.List;

/**
 * 单个象限的分组数据与实时统计。
 */
@Data
public class QuadrantGroupVO {

    /** 1-4，见设计方案 §3.3 */
    private Integer quadrant;

    private String name;

    private String action;

    /** 建议优先级，拖拽时前端据此提示 */
    private Integer suggestPriority;

    private List<TaskVO> tasks;

    /** 该象限任务总数 */
    private Long total;

    /** 该象限已完成数 */
    private Long doneCount;

    /**
     * 完成率。分母为 0 时返回 null，前端展示「—」而非 0%，
     * 见设计方案 §4.7。
     */
    private Double completionRate;
}
