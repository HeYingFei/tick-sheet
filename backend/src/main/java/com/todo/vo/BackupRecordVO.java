package com.todo.vo;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 备份记录视图。
 */
@Data
public class BackupRecordVO {

    private Long id;
    private String fileName;
    private Long fileSize;
    private String fileFormat;
    private String scopeDesc;
    private Integer taskCount;
    private Integer logCount;
    private OffsetDateTime createTime;
}
