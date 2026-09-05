package com.family.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 贷款新增/修改请求（房贷/车贷/消费贷）。
 * 月供与还款计划由后端按等额本息/等额本金公式测算（设计文档 9.2）。
 */
@Data
public class LoanDTO {

    @NotBlank(message = "贷款名称不能为空")
    @Size(max = 50, message = "贷款名称最长 50 字符")
    private String name;

    /** 贷款本金 */
    @NotNull(message = "本金不能为空")
    @DecimalMin(value = "0.01", message = "本金必须大于 0")
    @Digits(integer = 12, fraction = 2, message = "本金最多 2 位小数")
    private BigDecimal principal;

    /** 年利率, 如 0.0380 */
    @NotNull(message = "年利率不能为空")
    @DecimalMin(value = "0", message = "年利率不能为负")
    @Max(value = 1, message = "年利率须为小数（如 0.038）")
    private BigDecimal annualRate;

    /** 期数（月） */
    @NotNull(message = "期数不能为空")
    @Min(value = 1, message = "期数至少为 1 期")
    @Max(value = 600, message = "期数最多 600 期")
    private Integer termMonths;

    /** 起贷日期 */
    private LocalDate startDate;

    /** 还款方式: equal_installment 等额本息 / equal_principal 等额本金（默认等额本息） */
    private String repaymentType;

    /** 剩余本金（默认=本金，可按月手动更新） */
    private BigDecimal remainingPrincipal;

    /** 贷款机构 */
    @Size(max = 50, message = "贷款机构最长 50 字符")
    private String lender;
}
