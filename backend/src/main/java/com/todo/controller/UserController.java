package com.todo.controller;

import com.todo.common.PageResult;
import com.todo.common.R;
import com.todo.dto.UserQueryRequest;
import com.todo.dto.UserRequests;
import com.todo.service.UserService;
import com.todo.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理接口。
 *
 * <p>整条路径由 {@code AdminOnlyInterceptor} 拦截，仅超级管理员可用。
 * 不提供删除：误删会连带丢掉真实数据，需要收回权限时用禁用。
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public R<PageResult<UserVO>> page(UserQueryRequest query) {
        return R.ok(userService.page(query));
    }

    @PostMapping
    public R<UserVO> create(@Valid @RequestBody UserRequests.CreateRequest request) {
        return R.ok("用户已创建", userService.create(request));
    }

    @PutMapping("/{id}")
    public R<UserVO> update(@PathVariable Long id,
                            @Valid @RequestBody UserRequests.UpdateRequest request) {
        return R.ok("昵称已更新", userService.update(id, request));
    }

    @PutMapping("/{id}/password")
    public R<Void> resetPassword(@PathVariable Long id,
                                 @Valid @RequestBody UserRequests.ResetPasswordRequest request) {
        userService.resetPassword(id, request);
        return R.ok("密码已重置", null);
    }

    /** 状态变更沿用项目里 PATCH 子资源的约定，见 TaskController#changeStatus */
    @PatchMapping("/{id}/status")
    public R<UserVO> changeStatus(@PathVariable Long id,
                                  @Valid @RequestBody UserRequests.StatusChange request) {
        return R.ok("状态已更新", userService.changeStatus(id, request.getStatus()));
    }
}
