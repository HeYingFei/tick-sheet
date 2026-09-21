package com.todo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 备份记录表，见设计方案 §5.7。
 */
@Data
@TableName("backup_record")
public class BackupRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String fileName;

    /** 磁盘绝对路径，位于 app.backup.dir 之下 */
    private String filePath;

    private Long fileSize;

    /** json 或 excel */
    private String fileFormat;

    /** 备份范围描述，如「全量」 */
    private String scopeDesc;

    private Integer taskCount;

    private Integer logCount;

    /** 所属用户 ID，数据隔离 */
    private Long userId;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createTime;
}
