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

    /** 角色：admin-超级管理员，user-普通用户。前端据此决定是否显示用户管理菜单 */
    private String role;

    private String token;
}
