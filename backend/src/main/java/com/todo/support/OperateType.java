package com.todo.support;

/**
 * 日志操作类型，见设计方案 §5.5。
 */
public final class OperateType {

    public static final int CREATE = 1;
    public static final int MODIFY = 2;
    public static final int START = 3;
    public static final int DONE = 4;
    public static final int CANCEL = 5;
    public static final int DELETE = 6;
    public static final int RESTORE = 7;

    private OperateType() {
    }

    /**
     * 状态变更对应的操作类型：
     * 进入进行中记为「开始」，进入已完成记为「完成」，
     * 进入已取消记为「取消」，回到待办记为「恢复」。
     */
    public static int forStatus(int status) {
        return switch (status) {
            case TaskStatus.DOING -> START;
            case TaskStatus.DONE -> DONE;
            case TaskStatus.CANCELED -> CANCEL;
            default -> RESTORE;
        };
    }

    public static String text(int type) {
        return switch (type) {
            case CREATE -> "创建";
            case MODIFY -> "修改";
            case START -> "开始";
            case DONE -> "完成";
            case CANCEL -> "取消";
            case DELETE -> "删除";
            case RESTORE -> "恢复";
            default -> "未知";
        };
    }
}
