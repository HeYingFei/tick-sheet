package com.todo.common;

import lombok.Getter;

/**
 * 统一错误码，定义见设计方案 §6.2。
 */
@Getter
public enum ErrorCode {

    SUCCESS(200, "操作成功"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    PARAM_INVALID(400, "参数校验失败"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "数据冲突"),
    PAYLOAD_TOO_LARGE(413, "文件过大"),
    UNSUPPORTED_MEDIA_TYPE(415, "不支持的文件格式"),
    BUSINESS_ERROR(422, "业务规则不通过"),
    SERVER_ERROR(500, "服务端异常");

    private final int code;
    private final String msg;

    ErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
