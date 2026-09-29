package com.todo.vo;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 用户管理列表项。
 *
 * <p>刻意不含密码散列等敏感字段。
 */
@Data
public class UserVO {

    private Long id;

    private String username;

    private String nickname;

    private String role;

    /** 0-禁用 1-启用 */
    private Integer status;

    private OffsetDateTime createTime;
}
