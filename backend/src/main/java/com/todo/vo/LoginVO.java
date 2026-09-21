package com.todo.vo;

import lombok.Data;

/**
 * 登录响应。
 */
@Data
public class LoginVO {

    private Long id;

    private String username;

    private String nickname;

    private String avatarUrl;

    private String token;
}
