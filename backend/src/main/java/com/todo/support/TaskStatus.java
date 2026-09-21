package com.todo.support;

/**
 * 任务状态常量，见设计方案 §4.1。
 *
 * 注意：不存在「已逾期」状态。逾期是查询时派生状态，见 {@link OverdueCalculator}。
 */
public final class TaskStatus {

    /** 待办 */
    public static final int TODO = 0;
    /** 进行中 */
    public static final int DOING = 1;
    /** 已完成 */
    public static final int DONE = 2;
    /** 已取消 */
    public static final int CANCELED = 3;

    private TaskStatus() {
    }

    public static boolean isValid(Integer status) {
        return status != null && status >= TODO && status <= CANCELED;
    }

    /** 是否处于「未结束」状态，逾期判定只作用于这两类 */
    public static boolean isPending(Integer status) {
        return status != null && (status == TODO || status == DOING);
    }

    public static String text(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case TODO -> "待办";
            case DOING -> "进行中";
            case DONE -> "已完成";
            case CANCELED -> "已取消";
            default -> "未知";
        };
    }
}
