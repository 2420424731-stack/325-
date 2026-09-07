package com.family.finance.vo;

import lombok.Data;

/**
 * 登录/注册成功响应：token + 用户信息
 */
@Data
public class LoginVO {

    /** Sa-Token 令牌 */
    private String token;

    private UserVO user;
}
