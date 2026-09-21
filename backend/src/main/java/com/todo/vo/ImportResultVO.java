package com.todo.vo;

import lombok.Data;

/**
 * 数据导入结果。
 */
@Data
public class ImportResultVO {

    private Integer totalRows;
    private Integer successCount;
    private Integer duplicateCount;
    private Integer failCount;
    private String message;
}
