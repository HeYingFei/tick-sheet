package com.todo.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.todo.common.BizException;
import com.todo.config.AuthProperties;
import com.todo.dto.AuthRequests;
import com.todo.entity.SysUser;
import com.todo.mapper.SysUserMapper;
import com.todo.mapper.SystemConfigMapper;
import com.todo.support.DefaultConfig;
import com.todo.support.JwtUtil;
import com.todo.support.PasswordUtil;
import com.todo.support.UserContext;
import com.todo.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证服务。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final SystemConfigMapper systemConfigMapper;
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

    @Transactional
    public LoginVO register(AuthRequests.RegisterRequest request) {
        String username = request.getUsername().trim();

        Long exists = sysUserMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username));
        if (exists != null && exists > 0) {
            throw BizException.conflict("用户名已存在: " + username);
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(PasswordUtil.hash(request.getPassword()));
        user.setNickname(request.getNickname() != null && !request.getNickname().isBlank()
                ? request.getNickname().trim() : username);
        user.setAvatarUrl("");
        user.setStatus(1);
        sysUserMapper.insert(user);

        // 初始化默认配置
        DefaultConfig.buildFor(user.getId()).forEach(systemConfigMapper::insert);

        return buildLoginVO(user);
    }

    public LoginVO getCurrentUser() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw BizException.paramInvalid("未登录");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }

        LoginVO vo = new LoginVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        return vo;
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
        if (!PasswordUtil.verify(request.getOldPassword(), user.getPasswordHash())) {
            throw BizException.paramInvalid("原密码错误");
        }
        user.setPasswordHash(PasswordUtil.hash(request.getNewPassword()));
        sysUserMapper.updateById(user);
    }

    private LoginVO buildLoginVO(SysUser user) {
        LoginVO vo = new LoginVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setToken(JwtUtil.sign(user.getId(), user.getUsername(), authProperties.getSecret(),
                authProperties.getExpireMs()));
        return vo;
    }
}
