package com.todo.support;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * 逾期判定，见设计方案 §4.2。
 *
 * <p>核心约定：<b>逾期不落库</b>。{@code status} 只存 0/1/2/3 四个真实状态，
 * 「已逾期」由本类在查询时实时判定。
 *
 * <p>这样做的原因：不需要定时任务；应用停机期间不会产生错误数据；
 * 不存在「作业没跑导致逾期标记丢失」的一致性问题。
 */
public final class OverdueCalculator {

    private static final long SECONDS_PER_DAY = 86_400L;

    private OverdueCalculator() {
    }

    /**
     * 是否处于「逾期中」：未结束且已过截止时间。
     *
     * <pre>
     * overdue = (status IN (0,1)) AND (due_time IS NOT NULL) AND (due_time &lt; now())
     * </pre>
     */
    public static boolean isOverdue(Integer status, OffsetDateTime dueTime, OffsetDateTime now) {
        if (status == null || dueTime == null || now == null) {
            return false;
        }
        return TaskStatus.isPending(status) && dueTime.isBefore(now);
    }

    /**
     * 是否属于「逾期完成」：已完成，但完成时间晚于截止时间。
     * 这类任务不再显示为逾期，但仍计入逾期率统计。
     */
    public static boolean isCompletedLate(Integer status, OffsetDateTime dueTime,
                                          OffsetDateTime finishTime) {
        if (status == null || status != TaskStatus.DONE || dueTime == null || finishTime == null) {
            return false;
        }
        return finishTime.isAfter(dueTime);
    }

    /**
     * 是否计入逾期率统计口径：逾期中 + 逾期完成。
     */
    public static boolean countsAsOverdue(Integer status, OffsetDateTime dueTime,
                                          OffsetDateTime finishTime, OffsetDateTime now) {
        return isOverdue(status, dueTime, now) || isCompletedLate(status, dueTime, finishTime);
    }

    /**
     * 逾期天数，不足一天按一天计。
     * 仅对「逾期中」的任务有意义，其余情况返回 0。
     */
    public static long overdueDays(Integer status, OffsetDateTime dueTime, OffsetDateTime now) {
        if (status == null || dueTime == null || now == null || !TaskStatus.isPending(status)) {
            return 0;
        }
        return daysBetweenCeil(dueTime, now);
    }

    /**
     * 两个时点之间跨越的整天数，向上取整。{@code to} 不晚于 {@code from} 时返回 0。
     */
    public static long daysBetweenCeil(OffsetDateTime from, OffsetDateTime to) {
        if (from == null || to == null || !to.isAfter(from)) {
            return 0;
        }
        long seconds = Duration.between(from, to).getSeconds();
        return (seconds + SECONDS_PER_DAY - 1) / SECONDS_PER_DAY;
    }
}
