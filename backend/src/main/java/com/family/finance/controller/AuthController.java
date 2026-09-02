package com.family.finance.controller;

import com.family.finance.common.Result;
import com.family.finance.dto.LoginDTO;
import com.family.finance.dto.RegisterDTO;
import com.family.finance.service.AuthService;
import com.family.finance.vo.LoginVO;
import com.family.finance.vo.UserContextVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：注册 / 登录 / 当前用户
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 注册：自动创建家庭与户主成员，成功后自动登录 */
    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.ok(authService.register(dto));
    }

    /** 登录 */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.ok(authService.login(dto));
    }

    /** 当前登录用户上下文（用户 + 家庭 + 成员） */
    @GetMapping("/me")
    public Result<UserContextVO> me() {
        return Result.ok(authService.me());
    }

    /** 退出登录 */
    @PostMapping("/logout")
    public Result<Void> logout() {
        cn.dev33.satoken.stp.StpUtil.logout();
        return Result.ok();
    }
}
