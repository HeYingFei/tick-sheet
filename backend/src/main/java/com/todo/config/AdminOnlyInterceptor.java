package com.todo.config;

import com.todo.support.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理员权限拦截器。
 *
 * <p>注册在 {@code /api/users/**} 上，非超级管理员一律 403。
 * 这里只做判定——身份与角色由 {@link AuthInterceptor} 先行载入，两者注册顺序不可颠倒。
 */
@Component
public class AdminOnlyInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (UserContext.isAdmin()) {
            return true;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":403,\"msg\":\"无权访问\",\"data\":null,\"timestamp\":"
                + System.currentTimeMillis() + "}");
        return false;
    }
}
