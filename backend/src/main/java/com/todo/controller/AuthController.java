package com.todo.controller;

import com.todo.common.R;
import com.todo.dto.AuthRequests;
import com.todo.service.AuthService;
import com.todo.vo.LoginVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody AuthRequests.LoginRequest request) {
        return R.ok("登录成功", authService.login(request));
    }

    @PostMapping("/register")
    public R<LoginVO> register(@Valid @RequestBody AuthRequests.RegisterRequest request) {
        return R.ok("注册成功", authService.register(request));
    }

    @GetMapping("/me")
    public R<LoginVO> me() {
        return R.ok(authService.getCurrentUser());
    }

    @PutMapping("/profile")
    public R<Void> updateProfile(@Valid @RequestBody AuthRequests.UpdateProfileRequest request) {
        authService.updateProfile(request);
        return R.ok("资料已更新", null);
    }

    @PutMapping("/password")
    public R<Void> changePassword(@Valid @RequestBody AuthRequests.ChangePasswordRequest request) {
        authService.changePassword(request);
        return R.ok("密码已修改", null);
    }
}
