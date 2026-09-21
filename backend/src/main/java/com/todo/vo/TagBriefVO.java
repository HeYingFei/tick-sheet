package com.todo.vo;

import lombok.Data;

/**
 * 标签简要信息，内嵌在任务卡片中。
 */
@Data
public class TagBriefVO {

    private Long id;

    private String name;

    private String color;
}
