package com.juejin.common;

import lombok.Getter;

/**
 * 业务异常，code 会同时作为 HTTP 状态码返回
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        this(400, message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
