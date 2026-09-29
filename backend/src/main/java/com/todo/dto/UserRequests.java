package com.todo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户管理入参。仅超级管理员可用，见 docs/用户管理与权限设计方案.md。
 */
public final class UserRequests {

    private UserRequests() {
    }

    /** 管理员建号 */
    @Data
    public static class CreateRequest {

        @NotBlank(message = "用户名不能为空")
        @Size(min = 2, max = 50, message = "用户名长度 2-50")
        private String username;

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 64, message = "密码长度 6-64")
        private String password;

        /** 留空时取用户名 */
        @Size(max = 50, message = "昵称最长 50")
        private String nickname;
    }

    @Data
    public static class UpdateRequest {

        @NotBlank(message = "昵称不能为空")
        @Size(max = 50, message = "昵称最长 50")
        private String nickname;
    }

    /** 重置密码。管理员操作，不校验原密码——用户本人改密码仍需验原密码 */
    @Data
    public static class ResetPasswordRequest {

        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 64, message = "密码长度 6-64")
        private String password;
    }

    /** 0-禁用 1-启用 */
    @Data
    public static class StatusChange {

        @NotNull(message = "状态不能为空")
        private Integer status;
    }
}
