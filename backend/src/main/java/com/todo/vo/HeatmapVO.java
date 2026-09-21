package com.todo.vo;

import lombok.Data;

import java.util.List;

/**
 * 日历热力图数据。
 */
@Data
public class HeatmapVO {

    private List<Item> items;

    @Data
    public static class Item {
        /** 日期，格式 yyyy-MM-dd */
        private String date;
        private Long taskCount;
        private Long doneCount;
    }
}
