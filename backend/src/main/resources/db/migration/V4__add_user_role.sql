-- V4: 用户角色
-- admin 为超级管理员，只有它能访问用户管理接口。
-- 角色判定看字段而非用户名：改名不会丢权限，将来加「只读」「子管理员」也不必再改表。

ALTER TABLE sys_user ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'user';

COMMENT ON COLUMN sys_user.role IS '角色：admin-超级管理员，user-普通用户';

ALTER TABLE sys_user ADD CONSTRAINT ck_user_role CHECK (role IN ('admin', 'user'));

-- 把 admin 提为超管；万一没有同名账号，则取最早创建的账号，避免升级后无人可管。
-- 全新库执行到这里时 sys_user 还是空的，UPDATE 不会命中，由 DataInitializer 建号时写入 admin。
UPDATE sys_user SET role = 'admin'
WHERE id = COALESCE((SELECT id FROM sys_user WHERE username = 'admin' ORDER BY id LIMIT 1),
                    (SELECT id FROM sys_user ORDER BY id LIMIT 1));
