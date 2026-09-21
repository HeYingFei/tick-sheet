package com.todo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 任务标签关联表，见设计方案 §5.4。
 *
 * <p>复合主键 (task_id, tag_id)，不设自增 id。写入与删除通过
 * {@link com.todo.mapper.TaskTagMapper} 的自定义 SQL 完成。
 */
@Data
@TableName("task_tag")
public class TaskTag {

    private Long taskId;

    private Long tagId;
}
