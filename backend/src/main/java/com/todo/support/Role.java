package com.todo.support;

/**
 * 角色常量。
 *
 * <p>权限判定一律走这里，避免在业务代码里散落字面量；将来加角色只需在此扩展。
 */
public final class Role {

    /** 超级管理员：可访问用户管理 */
    public static final String ADMIN = "admin";

    /** 普通用户：只能管自己的数据 */
    public static final String USER = "user";

    private Role() {
    }
}
