package com.todo.dto;

import lombok.Data;

/**
 * 用户分页查询条件。
 */
@Data
public class UserQueryRequest {

    /** 关键词，同时匹配用户名与昵称 */
    private String keyword;

    private Integer page = 1;

    private Integer size = 20;
}
