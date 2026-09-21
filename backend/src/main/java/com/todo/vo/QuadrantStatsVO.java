package com.todo.vo;

import lombok.Data;

import java.util.List;

/**
 * 象限分布统计。
 */
@Data
public class QuadrantStatsVO {

    private List<Item> items;

    private Long total;

    @Data
    public static class Item {
        private Integer quadrant;
        private String name;
        private Long count;
        /** 占比百分比，total=0 时为 null */
        private Double percentage;
    }
}
