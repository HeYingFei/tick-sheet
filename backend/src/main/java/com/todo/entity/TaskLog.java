package com.todo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 任务操作日志表，时间线视图数据源，见设计方案 §5.5。
 */
@Data
@TableName("task_log")
public class TaskLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联任务ID。不设外键：任务被清理后日志仍需保留用于追溯 */
    private Long taskId;

    /** 1-创建 2-修改 3-开始 4-完成 5-取消 6-删除 7-恢复 */
    private Integer operateType;

    /** 面向用户的短语描述，如「将截止时间调整为 2026-09-20 18:00」 */
    private String operateDesc;

    /**
     * 变更快照 JSONB，形如 {"due_time":{"old":x,"new":y}}。
     * 用于时间线的字段级对比，见设计方案 §5.5。
     */
    private String operateDetail;

    /** 操作者标识，个人版固定 local，为多用户预留 */
    private String operator;

    /** 所属用户 ID，数据隔离 */
    private Long userId;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime operateTime;
}
