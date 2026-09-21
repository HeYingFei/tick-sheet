-- V2: 用户表 + 全表 user_id 隔离
-- 管理员账号由 Spring Boot StartupRunner 初始化，不在 SQL 里硬编码 BCrypt hash

-- ============================================================
-- 1. 用户表
-- ============================================================
CREATE TABLE sys_user (
    id            BIGSERIAL    PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nickname      VARCHAR(50)  NOT NULL DEFAULT '',
    avatar_url    VARCHAR(512) NOT NULL DEFAULT '',
    status        SMALLINT     NOT NULL DEFAULT 1,
    create_time   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    update_time   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_user_status CHECK (status IN (0, 1))
);

COMMENT ON TABLE  sys_user              IS '用户表';
COMMENT ON COLUMN sys_user.username     IS '用户名，登录用';
COMMENT ON COLUMN sys_user.password_hash IS 'BCrypt 加密后的密码';
COMMENT ON COLUMN sys_user.nickname     IS '昵称，展示用';
COMMENT ON COLUMN sys_user.avatar_url   IS '头像 URL';
COMMENT ON COLUMN sys_user.status       IS '状态：0-禁用，1-启用';

CREATE UNIQUE INDEX uk_user_username ON sys_user (username);

CREATE TRIGGER trg_sys_user_update_time
    BEFORE UPDATE ON sys_user
    FOR EACH ROW EXECUTE FUNCTION trg_set_update_time();

-- ============================================================
-- 2. 所有业务表加 user_id
-- ============================================================
ALTER TABLE task          ADD COLUMN user_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE tag           ADD COLUMN user_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE task_log      ADD COLUMN user_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE system_config ADD COLUMN user_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE backup_record ADD COLUMN user_id BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN task.user_id          IS '所属用户 ID';
COMMENT ON COLUMN tag.user_id           IS '所属用户 ID';
COMMENT ON COLUMN task_log.user_id      IS '所属用户 ID';
COMMENT ON COLUMN system_config.user_id IS '所属用户 ID';
COMMENT ON COLUMN backup_record.user_id IS '所属用户 ID';

-- ============================================================
-- 3. user_id 索引
-- ============================================================
CREATE INDEX idx_task_user_id      ON task (user_id)          WHERE is_delete = FALSE;
CREATE INDEX idx_tag_user_id       ON tag (user_id)           WHERE is_delete = FALSE;
CREATE INDEX idx_task_log_user_id  ON task_log (user_id);
CREATE INDEX idx_config_user_id    ON system_config (user_id);
CREATE INDEX idx_backup_user_id    ON backup_record (user_id);

-- ============================================================
-- 4. system_config 唯一约束调整：全局唯一 → (user_id, config_key) 唯一
-- ============================================================
ALTER TABLE system_config DROP CONSTRAINT IF EXISTS system_config_config_key_key;
CREATE UNIQUE INDEX uk_config_user_key ON system_config (user_id, config_key);

-- ============================================================
-- 5. 标签唯一索引：全局唯一 → (user_id, name) 唯一
-- ============================================================
DROP INDEX IF EXISTS uk_tag_name;
CREATE UNIQUE INDEX uk_tag_user_name ON tag (user_id, name) WHERE is_delete = FALSE;
