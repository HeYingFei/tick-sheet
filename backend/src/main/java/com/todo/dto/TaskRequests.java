package com.todo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 任务模块的批量与局部变更请求集合。
 */
public final class TaskRequests {

    private TaskRequests() {
    }

    /** 状态流转 */
    @Data
    public static class StatusChange {
        @NotNull(message = "目标状态不能为空")
        private Integer status;
    }

    /** 象限变更（看板拖拽） */
    @Data
    public static class QuadrantChange {
        @NotNull(message = "重要属性不能为空")
        private Boolean isImportant;

        @NotNull(message = "紧急属性不能为空")
        private Boolean isUrgent;
    }

    /** 排序变更 */
    @Data
    public static class OrderChange {
        @NotNull(message = "排序序号不能为空")
        private Integer sortOrder;
    }

    /** 批量状态变更 */
    @Data
    public static class BatchStatus {
        @NotNull(message = "任务 ID 列表不能为空")
        private List<Long> ids;

        @NotNull(message = "目标状态不能为空")
        private Integer status;
    }

    /** 批量删除 */
    @Data
    public static class BatchIds {
        @NotNull(message = "任务 ID 列表不能为空")
        private List<Long> ids;
    }

    /** 批量修改标签 */
    @Data
    public static class BatchTags {
        @NotNull(message = "任务 ID 列表不能为空")
        private List<Long> ids;

        @NotNull(message = "标签 ID 列表不能为空")
        private List<Long> tagIds;

        /** add-追加 remove-移除 replace-覆盖。默认 add */
        private String mode = "add";
    }
}
