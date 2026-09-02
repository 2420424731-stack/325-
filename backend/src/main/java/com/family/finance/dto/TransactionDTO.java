package com.family.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 收支记录新增/修改请求（PUT 为局部更新：null 字段不覆盖原值）
 */
@Data
public class TransactionDTO {

    /** 类型: 1 收入 / 2 支出 */
    @NotNull(message = "收支类型不能为空")
    private Integer type;

    /** 归属分类（叶子分类，须与 type 一致且启用） */
    @NotNull(message = "请选择收支分类")
    private Long categoryId;

    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "0.01", message = "金额必须大于 0")
    @Digits(integer = 10, fraction = 2, message = "金额最多 2 位小数")
    private BigDecimal amount;

    /** 业务发生日期 */
    @NotNull(message = "日期不能为空")
    private LocalDate bizDate;

    /** 经手成员 family_member.id（空=家庭整体） */
    private Long memberId;

    @Size(max = 100, message = "商家最长 100 字符")
    private String merchant;

    @Size(max = 50, message = "地区最长 50 字符")
    private String region;

    @Size(max = 100, message = "标签最长 100 字符")
    private String tags;

    @Size(max = 20, message = "支付方式最长 20 字符")
    private String paymentMethod;

    @Size(max = 255, message = "凭证图片地址最长 255 字符")
    private String image;

    @Size(max = 255, message = "备注最长 255 字符")
    private String note;
}
