package com.todo.support;

/**
 * 当前线程的用户上下文，由 {@link com.todo.config.AuthInterceptor} 在请求进入时设置，
 * 在 Controller / Service 中通过 {@link #getUserId()} 获取当前登录用户 ID。
 */
public final class UserContext {

    private static final ThreadLocal<Long> CURRENT_USER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void setUserId(Long userId) {
        CURRENT_USER.set(userId);
    }

    public static Long getUserId() {
        return CURRENT_USER.get();
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
