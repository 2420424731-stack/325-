package com.family.finance.common;

import lombok.Getter;

/**
 * 业务异常：Service 层抛出的可预期错误，由全局异常处理器统一转换为错误响应
 */
@Getter
public class BizException extends RuntimeException {

    private final Integer code;

    public BizException(String message) {
        super(message);
        this.code = 500;
    }

    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
