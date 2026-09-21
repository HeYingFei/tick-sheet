package com.todo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.todo.entity.TaskLog;
import org.apache.ibatis.annotations.Insert;

public interface TaskLogMapper extends BaseMapper<TaskLog> {

    /**
     * 写入日志。
     *
     * <p>{@code operate_detail} 是 jsonb 列，而 JDBC 会把 String 参数按 varchar 发送，
     * PostgreSQL 不接受 varchar 直接赋给 jsonb，因此这里显式 CAST。
     * 不依赖 JDBC URL 的 {@code stringtype=unspecified} 参数，避免换连接串就静默失效。
     */
    @Insert("INSERT INTO task_log (task_id, operate_type, operate_desc, operate_detail, operator, user_id, operate_time) "
            + "VALUES (#{taskId}, #{operateType}, #{operateDesc}, "
            + "CAST(#{operateDetail} AS jsonb), #{operator}, #{userId}, #{operateTime})")
    int insertLog(TaskLog log);
}
