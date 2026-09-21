-- V3: 时间格式配置改用 dayjs 记号
--
-- time_format 只被前端读取（交给 dayjs.format 渲染），但历史值写的是 Java 记号：
-- dayjs 不认识小写的 yyyy，会原样输出，并把 dd 当作星期缩写，
-- 界面上就显示成「yyyy-09-Th 17:57」这种乱码。
--
-- 这里只修正默认值，用户自定义过的值不满足条件，保持不动。
UPDATE system_config
SET config_value = 'YYYY-MM-DD HH:mm'
WHERE config_key = 'time_format'
  AND config_value = 'yyyy-MM-dd HH:mm';
