package com.family.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求：创建账号 + 家庭 + 户主成员 + 内置分类
 */
@Data
public class RegisterDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度须在 3-20 位")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度须在 6-32 位")
    private String password;

    @Size(max = 50, message = "昵称最长 50 字符")
    private String nickname;

    @NotBlank(message = "家庭名称不能为空")
    @Size(max = 50, message = "家庭名称最长 50 字符")
    private String familyName;
}
