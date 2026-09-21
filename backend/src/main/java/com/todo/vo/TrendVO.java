package com.todo.vo;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 趋势图数据点。
 */
@Data
public class TrendVO {

    private List<Item> items;

    @Data
    public static class Item {
        private LocalDate date;
        private Long newCount;
        private Long doneCount;
    }
}
