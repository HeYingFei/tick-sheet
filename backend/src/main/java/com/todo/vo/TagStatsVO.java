package com.todo.vo;

import lombok.Data;

import java.util.List;

/**
 * 标签分布统计。
 */
@Data
public class TagStatsVO {

    private List<Item> items;

    @Data
    public static class Item {
        private Long tagId;
        private String name;
        private String color;
        private Long count;
    }
}
