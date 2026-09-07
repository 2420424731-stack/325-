package com.family.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改家庭信息请求
 */
@Data
public class FamilyUpdateDTO {

    @NotBlank(message = "家庭名称不能为空")
    @Size(max = 50, message = "家庭名称最长 50 字符")
    private String name;

    @Size(max = 255, message = "描述最长 255 字符")
    private String description;
}
