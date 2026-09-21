package com.todo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 任务新增/编辑请求，见设计方案 §3.2。
 *
 * <p>编辑时需带 {@code version} 参与乐观锁校验，冲突返回 409。
 */
@Data
public class TaskSaveRequest {

    @NotBlank(message = "任务标题不能为空")
    @Size(max = 255, message = "任务标题长度不能超过 255")
    private String title;

    private String content;

    private OffsetDateTime startTime;

    private OffsetDateTime dueTime;

    /**
     * 优先级 1-极高 2-高 3-中 4-低。
     * 不传时按象限建议值填充，见设计方案 §4.3。
     */
    @Min(value = 1, message = "优先级取值范围为 1-4")
    @Max(value = 4, message = "优先级取值范围为 1-4")
    private Integer priority;

    private Boolean isImportant;

    private Boolean isUrgent;

    /** 标签 ID 列表，数量上限 20，见设计方案 §4.4 */
    private List<Long> tagIds;

    /** 乐观锁版本号，编辑时必传 */
    private Integer version;
}
