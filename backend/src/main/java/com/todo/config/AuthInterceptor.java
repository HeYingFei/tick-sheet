package com.todo.config;

import com.todo.entity.SysUser;
import com.todo.mapper.SysUserMapper;
import com.todo.support.JwtUtil;
import com.todo.support.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 认证拦截器。
 *
 * <p>从 Authorization 头提取 JWT，验证后按 userId 载入库中的用户，确认账号未被禁用，
 * 再把 userId 与 role 写入 {@link UserContext}。白名单路径（登录等）直接放行。
 *
 * <p>这里刻意每请求查一次主键：JWT 只承载身份，不承载状态。若把 status / role
 * 固化进 token，禁用账号与变更角色都要等 token 过期（最长 7 天）才生效。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthProperties authProperties;
    private final SysUserMapper sysUserMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        // OPTIONS 预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            Long userId = JwtUtil.verify(token, authProperties.getSecret());
            if (userId != null) {
                SysUser user = sysUserMapper.selectById(userId);
                // 账号被禁用时按未登录处理，使禁用立即生效
                if (user != null && user.getStatus() != null && user.getStatus() == 1) {
                    UserContext.set(userId, user.getRole());
                    return true;
                }
            }
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"msg\":\"未登录或登录已过期\",\"data\":null,\"timestamp\":"
                + System.currentTimeMillis() + "}");
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}
