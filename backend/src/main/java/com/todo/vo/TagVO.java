package com.todo.vo;

import lombok.Data;

/**
 * 标签视图对象。
 */
@Data
public class TagVO {

    private Long id;

    private String name;

    private String color;

    /** 关联的任务数，用于统计页与标签筛选下拉的展示 */
    private Long taskCount;
}
