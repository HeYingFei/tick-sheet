package com.todo.support;

import com.todo.common.BizException;

import java.util.Map;
import java.util.Set;

/**
 * 任务状态流转白名单，见设计方案 §4.1。
 *
 * <pre>
 * 0 待办   → 1 进行中 / 2 已完成 / 3 已取消
 * 1 进行中 → 0 待办 / 2 已完成 / 3 已取消
 * 2 已完成 → 0 待办（重新打开）
 * 3 已取消 → 0 待办（恢复）
 * </pre>
 *
 * 明确不允许：2 已完成 → 3 已取消，3 已取消 → 2 已完成。
 */
public final class StatusTransition {

    private static final Map<Integer, Set<Integer>> ALLOWED = Map.of(
            TaskStatus.TODO, Set.of(TaskStatus.DOING, TaskStatus.DONE, TaskStatus.CANCELED),
            TaskStatus.DOING, Set.of(TaskStatus.TODO, TaskStatus.DONE, TaskStatus.CANCELED),
            TaskStatus.DONE, Set.of(TaskStatus.TODO),
            TaskStatus.CANCELED, Set.of(TaskStatus.TODO)
    );

    private StatusTransition() {
    }

    /**
     * 同状态视为幂等空操作，允许通过。
     * 这样批量操作中混入已完成任务时不会整体失败。
     */
    public static boolean canTransit(int from, int to) {
        if (from == to) {
            return true;
        }
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    /**
     * 校验流转合法性，非法时抛出 422。
     */
    public static void assertCanTransit(int from, int to) {
        if (!TaskStatus.isValid(from) || !TaskStatus.isValid(to)) {
            throw BizException.business("非法的任务状态值: " + from + " -> " + to);
        }
        if (!canTransit(from, to)) {
            throw BizException.business(
                    "不允许的状态流转：" + TaskStatus.text(from) + " 无法变更为 " + TaskStatus.text(to));
        }
    }
}
