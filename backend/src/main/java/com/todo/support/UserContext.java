package com.todo.support;

/**
 * 当前线程的用户上下文，由 {@link com.todo.config.AuthInterceptor} 在请求进入时设置，
 * 在 Controller / Service 中通过 {@link #getUserId()} 与 {@link #isAdmin()} 获取当前身份。
 *
 * <p>角色随每次请求从库里载入，因此禁用账号与变更角色都能立即生效，不必等 token 过期。
 */
public final class UserContext {

    private static final ThreadLocal<Long> CURRENT_USER = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_ROLE = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Long userId, String role) {
        CURRENT_USER.set(userId);
        CURRENT_ROLE.set(role);
    }

    public static Long getUserId() {
        return CURRENT_USER.get();
    }

    public static String getRole() {
        return CURRENT_ROLE.get();
    }

    /** 当前请求是否来自超级管理员 */
    public static boolean isAdmin() {
        return Role.ADMIN.equals(CURRENT_ROLE.get());
    }

    public static void clear() {
        CURRENT_USER.remove();
        CURRENT_ROLE.remove();
    }
}
