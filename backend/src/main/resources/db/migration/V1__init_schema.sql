-- =============================================================================
-- 现代化个人任务管理系统 - 初始化建表脚本
-- 数据库：PostgreSQL 14+
-- 说明：本脚本为 Flyway 迁移脚本 V1，请勿手工在数据库直接改表结构。
--
-- 注意：PostgreSQL 不支持 MySQL 的列内联 COMMENT 语法，
--       所有字段注释必须使用独立的 COMMENT ON COLUMN 语句。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 任务主表 task
-- -----------------------------------------------------------------------------
CREATE TABLE task (
    id           BIGSERIAL    PRIMARY KEY,
    title        VARCHAR(255) NOT NULL,
    content      TEXT         NOT NULL DEFAULT '',
    start_time   TIMESTAMPTZ  NULL,
    due_time     TIMESTAMPTZ  NULL,
    finish_time  TIMESTAMPTZ  NULL,
    status       SMALLINT     NOT NULL DEFAULT 0,
    is_important BOOLEAN      NOT NULL DEFAULT FALSE,
    is_urgent    BOOLEAN      NOT NULL DEFAULT FALSE,
    priority     SMALLINT     NOT NULL DEFAULT 3,
    sort_order   INTEGER      NOT NULL DEFAULT 0,
    is_delete    BOOLEAN      NOT NULL DEFAULT FALSE,
    version      INTEGER      NOT NULL DEFAULT 0,
    create_time  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    update_time  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_task_status     CHECK (status IN (0, 1, 2, 3)),
    CONSTRAINT ck_task_priority   CHECK (priority BETWEEN 1 AND 4),
    CONSTRAINT ck_task_sort_order CHECK (sort_order >= 0),
    CONSTRAINT ck_task_time_order CHECK (
        due_time IS NULL OR start_time IS NULL OR due_time >= start_time
    )
);

COMMENT ON TABLE  task              IS '任务主表';
COMMENT ON COLUMN task.id           IS '任务主键ID';
COMMENT ON COLUMN task.title        IS '任务标题';
COMMENT ON COLUMN task.content      IS '任务详细备注内容';
COMMENT ON COLUMN task.start_time   IS '任务开始时间';
COMMENT ON COLUMN task.due_time     IS '任务截止时间';
COMMENT ON COLUMN task.finish_time  IS '任务完成时间，进入已完成状态时写入，退出时清空';
COMMENT ON COLUMN task.status       IS '任务状态：0-待办，1-进行中，2-已完成，3-已取消。注意：逾期不落库，为查询时派生状态';
COMMENT ON COLUMN task.is_important IS '是否重要：false-否，true-是';
COMMENT ON COLUMN task.is_urgent    IS '是否紧急：false-否，true-是';
COMMENT ON COLUMN task.priority     IS '优先级：1-极高，2-高，3-中，4-低';
COMMENT ON COLUMN task.sort_order   IS '手动拖拽排序序号，同象限内升序排列';
COMMENT ON COLUMN task.is_delete    IS '逻辑删除：false-未删除，true-已删除';
COMMENT ON COLUMN task.version      IS '乐观锁版本号，每次更新 +1';
COMMENT ON COLUMN task.create_time  IS '任务创建时间';
COMMENT ON COLUMN task.update_time  IS '任务更新时间，由触发器自动维护';


-- -----------------------------------------------------------------------------
-- 2. 标签表 tag
-- -----------------------------------------------------------------------------
CREATE TABLE tag (
    id          BIGSERIAL   PRIMARY KEY,
    name        VARCHAR(50) NOT NULL,
    color       VARCHAR(20) NOT NULL DEFAULT '#3B82F6',
    is_delete   BOOLEAN     NOT NULL DEFAULT FALSE,
    create_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    update_time TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE  tag             IS '标签表';
COMMENT ON COLUMN tag.id          IS '标签主键ID';
COMMENT ON COLUMN tag.name        IS '标签名称，未删除范围内唯一';
COMMENT ON COLUMN tag.color       IS '标签颜色，十六进制色值，如 #3B82F6';
COMMENT ON COLUMN tag.is_delete   IS '逻辑删除：false-未删除，true-已删除';
COMMENT ON COLUMN tag.create_time IS '创建时间';
COMMENT ON COLUMN tag.update_time IS '更新时间，由触发器自动维护';

CREATE UNIQUE INDEX uk_tag_name ON tag (name) WHERE is_delete = FALSE;


-- -----------------------------------------------------------------------------
-- 3. 任务标签关联表 task_tag
-- -----------------------------------------------------------------------------
CREATE TABLE task_tag (
    task_id BIGINT NOT NULL REFERENCES task(id) ON DELETE CASCADE,
    tag_id  BIGINT NOT NULL REFERENCES tag(id)  ON DELETE CASCADE,
    PRIMARY KEY (task_id, tag_id)
);

COMMENT ON TABLE  task_tag         IS '任务标签关联表，多对多关系';
COMMENT ON COLUMN task_tag.task_id IS '任务ID';
COMMENT ON COLUMN task_tag.tag_id  IS '标签ID';


-- -----------------------------------------------------------------------------
-- 4. 任务操作日志表 task_log
-- -----------------------------------------------------------------------------
CREATE TABLE task_log (
    id             BIGSERIAL    PRIMARY KEY,
    task_id        BIGINT       NOT NULL,
    operate_type   SMALLINT     NOT NULL,
    operate_desc   VARCHAR(255) NOT NULL,
    operate_detail JSONB        NULL,
    operator       VARCHAR(50)  NOT NULL DEFAULT 'local',
    operate_time   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_log_operate_type CHECK (operate_type BETWEEN 1 AND 7)
);

COMMENT ON TABLE  task_log                IS '任务操作日志表，时间线视图数据源';
COMMENT ON COLUMN task_log.id             IS '日志主键ID';
COMMENT ON COLUMN task_log.task_id        IS '关联任务ID。刻意不设外键：任务被物理清理后日志仍需保留用于追溯';
COMMENT ON COLUMN task_log.operate_type   IS '操作类型：1-创建，2-修改，3-开始，4-完成，5-取消，6-删除，7-恢复';
COMMENT ON COLUMN task_log.operate_desc   IS '操作描述，面向用户的短语，如「将截止时间调整为 2026-09-20 18:00」';
COMMENT ON COLUMN task_log.operate_detail IS '变更快照 JSONB，形如 {"field":{"old":x,"new":y}}，用于时间线字段级对比';
COMMENT ON COLUMN task_log.operator       IS '操作者标识，个人版固定 local，为多用户预留';
COMMENT ON COLUMN task_log.operate_time   IS '操作发生时间';


-- -----------------------------------------------------------------------------
-- 5. 系统配置表 system_config
-- -----------------------------------------------------------------------------
CREATE TABLE system_config (
    id           BIGSERIAL    PRIMARY KEY,
    config_key   VARCHAR(100) NOT NULL UNIQUE,
    config_value VARCHAR(512) NOT NULL DEFAULT '',
    config_desc  VARCHAR(255) NOT NULL DEFAULT '',
    update_time  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE  system_config              IS '系统配置表';
COMMENT ON COLUMN system_config.id           IS '配置主键ID';
COMMENT ON COLUMN system_config.config_key   IS '配置键名，唯一';
COMMENT ON COLUMN system_config.config_value IS '配置值';
COMMENT ON COLUMN system_config.config_desc  IS '配置描述';
COMMENT ON COLUMN system_config.update_time  IS '更新时间，由触发器自动维护';


-- -----------------------------------------------------------------------------
-- 6. 备份记录表 backup_record
-- -----------------------------------------------------------------------------
CREATE TABLE backup_record (
    id          BIGSERIAL    PRIMARY KEY,
    file_name   VARCHAR(255) NOT NULL,
    file_path   VARCHAR(512) NOT NULL,
    file_size   BIGINT       NOT NULL DEFAULT 0,
    file_format VARCHAR(10)  NOT NULL,
    scope_desc  VARCHAR(255) NOT NULL DEFAULT '全量',
    task_count  INTEGER      NOT NULL DEFAULT 0,
    log_count   INTEGER      NOT NULL DEFAULT 0,
    create_time TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_backup_format CHECK (file_format IN ('json', 'excel'))
);

COMMENT ON TABLE  backup_record             IS '备份记录表，支撑设置页的备份列表与一键恢复';
COMMENT ON COLUMN backup_record.id          IS '备份主键ID';
COMMENT ON COLUMN backup_record.file_name   IS '备份文件名';
COMMENT ON COLUMN backup_record.file_path   IS '备份文件磁盘绝对路径，位于 app.backup.dir 之下';
COMMENT ON COLUMN backup_record.file_size   IS '文件大小，单位字节';
COMMENT ON COLUMN backup_record.file_format IS '文件格式：json 或 excel';
COMMENT ON COLUMN backup_record.scope_desc  IS '备份范围描述，如「全量」或「状态=已完成」';
COMMENT ON COLUMN backup_record.task_count  IS '备份包含的任务条数';
COMMENT ON COLUMN backup_record.log_count   IS '备份包含的日志条数';
COMMENT ON COLUMN backup_record.create_time IS '备份创建时间';


-- -----------------------------------------------------------------------------
-- 7. 索引
-- -----------------------------------------------------------------------------
-- 任务表：部分索引，软删除数据不进索引
CREATE INDEX idx_task_status      ON task (status)                  WHERE is_delete = FALSE;
CREATE INDEX idx_task_due_time    ON task (due_time)                WHERE is_delete = FALSE;
CREATE INDEX idx_task_create_time ON task (create_time DESC)        WHERE is_delete = FALSE;
CREATE INDEX idx_task_quadrant    ON task (is_important, is_urgent) WHERE is_delete = FALSE;
CREATE INDEX idx_task_priority    ON task (priority)                WHERE is_delete = FALSE;

-- 逾期查询专用索引（见设计方案 §4.2）
CREATE INDEX idx_task_overdue ON task (due_time)
    WHERE is_delete = FALSE AND status IN (0, 1);

-- 标题模糊搜索
CREATE INDEX idx_task_title_trgm ON task (lower(title)) WHERE is_delete = FALSE;

-- 标签关联表
CREATE INDEX idx_task_tag_tag ON task_tag (tag_id);

-- 日志表
CREATE INDEX idx_log_task_id      ON task_log (task_id);
CREATE INDEX idx_log_operate_time ON task_log (operate_time DESC);
CREATE INDEX idx_log_type_time    ON task_log (operate_type, operate_time DESC);
CREATE INDEX idx_log_detail_gin   ON task_log USING GIN (operate_detail);

-- 备份记录表
CREATE INDEX idx_backup_create_time ON backup_record (create_time DESC);


-- -----------------------------------------------------------------------------
-- 8. 触发器：自动维护 update_time
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION trg_set_update_time() RETURNS trigger AS $$
BEGIN
    NEW.update_time = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION trg_set_update_time() IS '通用触发器函数：更新时自动写入 update_time';

CREATE TRIGGER trg_task_update_time
    BEFORE UPDATE ON task
    FOR EACH ROW EXECUTE FUNCTION trg_set_update_time();

CREATE TRIGGER trg_tag_update_time
    BEFORE UPDATE ON tag
    FOR EACH ROW EXECUTE FUNCTION trg_set_update_time();

CREATE TRIGGER trg_config_update_time
    BEFORE UPDATE ON system_config
    FOR EACH ROW EXECUTE FUNCTION trg_set_update_time();


-- -----------------------------------------------------------------------------
-- 9. 初始化数据
-- -----------------------------------------------------------------------------
INSERT INTO system_config (config_key, config_value, config_desc) VALUES
('theme_mode',       'light',            '系统主题：light-浅色，dark-深色'),
('default_priority', '3',                '默认任务优先级：1-极高，2-高，3-中，4-低'),
('time_format',      'yyyy-MM-dd HH:mm', '时间展示格式'),
('week_start',       '1',                '周起始日：1-周一，7-周日'),
('calendar_field',   'due_time',         '日历视图映射字段：due_time-截止时间，start_time-开始时间');

INSERT INTO tag (name, color) VALUES
('工作', '#3B82F6'),
('学习', '#10B981'),
('生活', '#F59E0B'),
('紧急', '#EF4444');
