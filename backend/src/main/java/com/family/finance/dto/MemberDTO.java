package com.family.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 家庭成员新增/修改请求（user_id 绑定账号功能暂未开放，成员为家庭内登记信息）
 */
@Data
public class MemberDTO {

    @NotBlank(message = "成员姓名不能为空")
    @Size(max = 50, message = "成员姓名最长 50 字符")
    private String name;

    /** 关系: 户主/配偶/子女/父母/其他 */
    @Size(max = 20, message = "关系最长 20 字符")
    private String relation;

    /** 出生日期（可空） */
    private LocalDate birthday;

    /** 排序（null 默认 0） */
    private Integer sortOrder;

    /** 状态: 1 启用 0 停用（null 默认 1） */
    private Integer status;
}
