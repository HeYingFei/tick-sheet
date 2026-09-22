package com.todo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 数据库结构验证。
 *
 * 作用有三：
 * 1. 验证 V1__init_schema.sql 在真实 PostgreSQL 下语法正确（这是原稿最大的问题所在）
 * 2. 验证 Flyway 迁移可执行
 * 3. 验证约束、触发器、索引、初始化数据确实落地
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SchemaVerificationTest {

    private static final List<String> EXPECTED_TABLES = List.of(
            "backup_record", "system_config", "tag", "task", "task_log", "task_tag");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayShouldCreateAllSixTables() {
        List<String> tables = jdbcTemplate.queryForList(
                "select table_name from information_schema.tables "
                        + "where table_schema = 'public' and table_type = 'BASE TABLE' order by table_name",
                String.class);
        assertThat(tables).containsAll(EXPECTED_TABLES);
    }

    @Test
    void fieldCommentsShouldBeApplied() {
        // 原稿使用 MySQL 内联 COMMENT 语法，此处确认注释确实写入了 PostgreSQL 元数据
        String comment = jdbcTemplate.queryForObject(
                "select col_description('task'::regclass, ordinal_position) "
                        + "from information_schema.columns "
                        + "where table_name = 'task' and column_name = 'status'",
                String.class);
        assertThat(comment).isNotNull().contains("逾期不落库");
    }

    @Test
    void systemConfigShouldBeSeeded() {
        // 只断言种子键存在，不断言全局行数：配置按用户各存一份，
        // 注册第二个账号就会变成 10 行，用精确行数会误报。
        List<String> keys = jdbcTemplate.queryForList(
                "select distinct config_key from system_config", String.class);
        assertThat(keys).contains(
                "theme_mode", "default_priority", "time_format", "week_start", "calendar_field");
    }

    @Test
    void tagsShouldBeSeeded() {
        // 同理：标签是用户可增删的业务数据（V1 种下 工作/学习/生活/紧急），
        // 断言总数会被用户新增标签打破，这里只断言种子标签存在。
        // 不加 is_delete 过滤：用户删掉某个种子标签时行仍在，属正常情况。
        List<String> names = jdbcTemplate.queryForList(
                "select distinct name from tag", String.class);
        assertThat(names).contains("工作", "学习", "生活", "紧急");
    }

    @Test
    void updateTimeTriggersShouldBeInstalled() {
        List<String> triggers = jdbcTemplate.queryForList(
                "select tgname from pg_trigger where not tgisinternal order by tgname", String.class);
        assertThat(triggers).contains(
                "trg_task_update_time", "trg_tag_update_time", "trg_config_update_time");
    }

    @Test
    void keyIndexesShouldExist() {
        List<String> indexes = jdbcTemplate.queryForList(
                "select indexname from pg_indexes where schemaname = 'public' order by indexname",
                String.class);
        assertThat(indexes).contains(
                "idx_task_overdue",     // 逾期查询专用部分索引
                "idx_task_quadrant",    // 象限看板
                "uk_tag_user_name",     // 标签名部分唯一索引，V2 起已收敛到用户维度
                "idx_log_detail_gin");  // JSONB 快照 GIN 索引
    }

    @Test
    @Transactional
    void checkConstraintShouldRejectInvalidStatus() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into task (title, status) values ('非法状态测试', 9)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void checkConstraintShouldRejectDueTimeBeforeStartTime() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into task (title, start_time, due_time) values "
                        + "('时间顺序测试', '2026-09-20 10:00:00+08', '2026-09-19 10:00:00+08')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void updateTimeShouldBeMaintainedByTrigger() {
        jdbcTemplate.update("insert into tag (name, color) values ('触发器验证标签', '#000000')");
        Long id = jdbcTemplate.queryForObject(
                "select id from tag where name = '触发器验证标签'", Long.class);

        jdbcTemplate.update("update tag set update_time = '2000-01-01 00:00:00+08' where id = ?", id);
        jdbcTemplate.update("update tag set color = '#111111' where id = ?", id);

        // 即使 SQL 显式写入旧值，BEFORE UPDATE 触发器也应把它覆写为当前时间
        java.sql.Timestamp updateTime = jdbcTemplate.queryForObject(
                "select update_time from tag where id = ?", java.sql.Timestamp.class, id);
        assertThat(updateTime).isNotNull();
        assertThat(updateTime.toInstant()).isAfter(java.time.Instant.parse("2020-01-01T00:00:00Z"));
    }
}
