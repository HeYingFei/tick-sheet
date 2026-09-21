package com.todo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 任务主表。字段含义见设计方案 §5.3。
 *
 * <p>注意 {@code status} 不含「已逾期」：逾期是查询时派生状态，见
 * {@link com.todo.support.OverdueCalculator}。
 */
@Data
@TableName("task")
public class Task {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    private OffsetDateTime startTime;

    private OffsetDateTime dueTime;

    /** 进入已完成状态时写入，退出时清空 */
    private OffsetDateTime finishTime;

    /** 0-待办 1-进行中 2-已完成 3-已取消 */
    private Integer status;

    private Boolean isImportant;

    private Boolean isUrgent;

    /** 1-极高 2-高 3-中 4-低 */
    private Integer priority;

    /** 手动拖拽排序序号，同象限内升序 */
    private Integer sortOrder;

    @TableLogic
    private Boolean isDelete;

    /** 乐观锁版本号，冲突时返回 409 */
    @Version
    private Integer version;

    /** 所属用户 ID，数据隔离 */
    private Long userId;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updateTime;
}
