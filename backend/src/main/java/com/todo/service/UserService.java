package com.todo.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.todo.common.BizException;
import com.todo.common.PageResult;
import com.todo.dto.UserQueryRequest;
import com.todo.dto.UserRequests;
import com.todo.entity.SysUser;
import com.todo.mapper.SysUserMapper;
import com.todo.mapper.SystemConfigMapper;
import com.todo.support.DefaultConfig;
import com.todo.support.PasswordUtil;
import com.todo.support.Role;
import com.todo.support.UserContext;
import com.todo.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 用户管理服务。
 *
 * <p>权限由 {@link com.todo.config.AdminOnlyInterceptor} 在入口拦下，这里只处理业务规则。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private static final int STATUS_DISABLED = 0;
    private static final int STATUS_ENABLED = 1;
    private static final int MAX_PAGE_SIZE = 100;

    private final SysUserMapper sysUserMapper;
    private final SystemConfigMapper systemConfigMapper;

    public PageResult<UserVO> page(UserQueryRequest query) {
        String keyword = query.getKeyword() == null ? "" : query.getKeyword().trim();
        long current = query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
        long size = query.getSize() == null || query.getSize() < 1
                ? 20 : Math.min(query.getSize(), MAX_PAGE_SIZE);

        Page<SysUser> result = sysUserMapper.selectPage(new Page<>(current, size),
                Wrappers.<SysUser>lambdaQuery()
                        .and(!keyword.isEmpty(), w -> w
                                .like(SysUser::getUsername, keyword)
                                .or()
                                .like(SysUser::getNickname, keyword))
                        .orderByAsc(SysUser::getId));

        return PageResult.of(result, this::toVO);
    }

    /**
     * 管理员建号。
     *
     * <p>新账号与原先自助注册时创建的一致：同样初始化一份默认配置，
     * 否则该用户进设置页会读到空配置。
     */
    @Transactional
    public UserVO create(UserRequests.CreateRequest request) {
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
        user.setStatus(STATUS_ENABLED);
        // 管理员建出来的都是普通用户；角色不提供编辑入口
        user.setRole(Role.USER);
        sysUserMapper.insert(user);

        DefaultConfig.buildFor(user.getId()).forEach(systemConfigMapper::insert);
        return toVO(user);
    }

    public UserVO update(Long id, UserRequests.UpdateRequest request) {
        SysUser user = require(id);
        user.setNickname(request.getNickname().trim());
        sysUserMapper.updateById(user);
        return toVO(user);
    }

    /** 重置密码：管理员操作，不校验原密码 */
    public void resetPassword(Long id, UserRequests.ResetPasswordRequest request) {
        SysUser user = require(id);
        user.setPasswordHash(PasswordUtil.hash(request.getPassword()));
        sysUserMapper.updateById(user);
    }

    /**
     * 启用 / 禁用。
     *
     * <p>不允许禁用自己：把自己锁在门外后只能改库恢复。
     * 禁用立即生效——认证拦截器每次请求都会校验账号状态。
     */
    public UserVO changeStatus(Long id, Integer status) {
        if (status == null || (status != STATUS_DISABLED && status != STATUS_ENABLED)) {
            throw BizException.paramInvalid("状态只能是 0-禁用 或 1-启用");
        }

        SysUser user = require(id);
        if (status == STATUS_DISABLED && Objects.equals(user.getId(), UserContext.getUserId())) {
            throw BizException.business("不能禁用当前登录的账号");
        }

        user.setStatus(status);
        sysUserMapper.updateById(user);
        return toVO(user);
    }

    private SysUser require(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw BizException.notFound("用户不存在: " + id);
        }
        return user;
    }

    private UserVO toVO(SysUser user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}
