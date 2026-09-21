package com.todo.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 数据导出结构（JSON 格式）。
 */
@Data
public class ExportDataDTO {

    private String version = "1.0";
    private String exportTime;
    private List<TaskExportDTO> tasks;
    private List<Map<String, Object>> tags;
    private List<Map<String, Object>> taskTags;
    private List<Map<String, Object>> logs;

    @Data
    public static class TaskExportDTO {
        private Long id;
        private String title;
        private String content;
        private String startTime;
        private String dueTime;
        private String finishTime;
        private Integer status;
        private Boolean isImportant;
        private Boolean isUrgent;
        private Integer priority;
        private Integer sortOrder;
        private String createTime;
        private String updateTime;
    }
}
