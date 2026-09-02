package com.family.finance.service;

import com.family.finance.dto.LoginDTO;
import com.family.finance.dto.RegisterDTO;
import com.family.finance.vo.LoginVO;
import com.family.finance.vo.UserContextVO;

/**
 * 认证服务：注册（自动建家庭/户主/内置分类）、登录、当前用户上下文
 */
public interface AuthService {

    /**
     * 注册：创建账号（管理员）+ 家庭 + 户主成员 + 初始化内置收支分类，注册成功自动登录
     */
    LoginVO register(RegisterDTO dto);

    /**
     * 登录：校验用户名密码，签发 Sa-Token
     */
    LoginVO login(LoginDTO dto);

    /**
     * 当前登录用户上下文：用户 + 家庭 + 成员列表
     */
    UserContextVO me();
}
