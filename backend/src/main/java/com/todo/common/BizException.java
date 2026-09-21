package com.todo.common;

import lombok.Getter;

/**
 * 业务异常。由 {@link GlobalExceptionHandler} 统一转换为标准返回体。
 */
@Getter
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMsg());
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public static BizException notFound(String message) {
        return new BizException(ErrorCode.NOT_FOUND, message);
    }

    public static BizException paramInvalid(String message) {
        return new BizException(ErrorCode.PARAM_INVALID, message);
    }

    public static BizException conflict(String message) {
        return new BizException(ErrorCode.CONFLICT, message);
    }

    public static BizException unsupported(String message) {
        return new BizException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, message);
    }

    public static BizException business(String message) {
        return new BizException(ErrorCode.BUSINESS_ERROR, message);
    }
}
