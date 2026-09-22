package com.todo.vo;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 趋势图数据点。
 */
@Data
public class TrendVO {

    /** 实际生效的天数：未指定时取用户配置，并收敛到 1-90。前端据此渲染标题，避免与实际曲线不一致 */
    private Integer days;

    private List<Item> items;

    @Data
    public static class Item {
        private LocalDate date;
        private Long newCount;
        private Long doneCount;
    }
}
