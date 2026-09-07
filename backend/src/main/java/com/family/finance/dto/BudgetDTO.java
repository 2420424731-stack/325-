package com.family.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 预算新增/修改请求。
 * categoryId 为空 = 家庭总预算；同一家庭同一月份同一分类唯一（数据库唯一键兜底）。
 */
@Data
public class BudgetDTO {

    /** 预算分类 category.id（空=家庭总预算） */
    private Long categoryId;

    /** 预算月份, 如 2026-09 */
    @NotNull(message = "预算月份不能为空")
    @Pattern(regexp = "\\d{4}-\\d{2}", message = "预算月份格式须为 yyyy-MM")
    private String budgetMonth;

    /** 预算金额 */
    @NotNull(message = "预算金额不能为空")
    @DecimalMin(value = "0.01", message = "预算金额必须大于 0")
    @Digits(integer = 10, fraction = 2, message = "预算金额最多 2 位小数")
    private BigDecimal amount;
}
