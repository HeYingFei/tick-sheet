package com.todo.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 自动填充创建时间与更新时间。
 *
 * <p>数据库侧对 {@code update_time} 另有 BEFORE UPDATE 触发器兜底，
 * 两者写入的都是当前时刻，互不冲突；触发器保证即使走原生 SQL 也能正确维护该字段。
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Override
    public void insertFill(MetaObject metaObject) {
        OffsetDateTime now = OffsetDateTime.now(ZONE);
        strictInsertFill(metaObject, "createTime", OffsetDateTime.class, now);
        strictInsertFill(metaObject, "updateTime", OffsetDateTime.class, now);
        strictInsertFill(metaObject, "operateTime", OffsetDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updateTime", OffsetDateTime.class, OffsetDateTime.now(ZONE));
    }
}
