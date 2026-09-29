package com.todo.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.todo.common.BizException;
import com.todo.config.AuthProperties;
import com.todo.dto.AuthRequests;
import com.todo.entity.SysUser;
import com.todo.mapper.SysUserMapper;
import com.todo.support.JwtUtil;
import com.todo.support.PasswordUtil;
import com.todo.support.UserContext;
import com.todo.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 认证服务。
 *
 * <p>注册入口已关闭：账号只能由超级管理员在用户管理中创建，见
 * {@link UserService#create(com.todo.dto.UserRequests.CreateRequest)}。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final AuthProperties authProperties;

    public LoginVO login(AuthRequests.LoginRequest request) {
        SysUser user = sysUserMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, request.getUsername().trim()));

        if (user == null || !PasswordUtil.verify(request.getPassword(), user.getPasswordHash())) {
            throw BizException.paramInvalid("用户名或密码错误");
        }
        if (user.getStatus() != 1) {
            throw BizException.business("账号已被禁用");
        }

        return buildLoginVO(user);
    }

    /**
     * 当前登录用户。
     *
     * <p>角色取自库中的实时值，用户管理里改了角色，这里下一次请求就能读到。
     */
    public LoginVO getCurrentUser() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw BizException.paramInvalid("未登录");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        return buildProfileVO(user);
    }

    public void updateProfile(AuthRequests.UpdateProfileRequest request) {
        Long userId = UserContext.getUserId();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        if (request.getNickname() != null) {
            user.setNickname(request.getNickname().trim());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl().trim());
        }
        sysUserMapper.updateById(user);
    }

    public void changePassword(AuthRequests.ChangePasswordRequest request) {
        Long userId = UserContext.getUserId();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        // 用户改自己的密码必须验原密码；管理员重置他人密码走 UserService
        if (!PasswordUtil.verify(request.getOldPassword(), user.getPasswordHash())) {
            throw BizException.paramInvalid("原密码错误");
        }
        user.setPasswordHash(PasswordUtil.hash(request.getNewPassword()));
        sysUserMapper.updateById(user);
    }

    private LoginVO buildLoginVO(SysUser user) {
        LoginVO vo = buildProfileVO(user);
        vo.setToken(JwtUtil.sign(user.getId(), user.getUsername(), authProperties.getSecret(),
                authProperties.getExpireMs()));
        return vo;
    }

    /** 资料部分：登录、取当前用户、用户管理列表共用同一套字段 */
    private LoginVO buildProfileVO(SysUser user) {
        LoginVO vo = new LoginVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setRole(user.getRole());
        return vo;
    }
}
