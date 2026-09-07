package com.family.finance.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 预算执行情况（设计文档 F11）：预算额 vs 实际支出（分类预算含子孙分类）与执行率
 */
@Data
public class BudgetExecutionVO {

    private Long budgetId;
    /** 预算分类 id（空=家庭总预算） */
    private Long categoryId;
    /** 分类名（家庭总预算为「家庭总支出」） */
    private String categoryName;
    /** 预算月份 */
    private String budgetMonth;
    /** 预算金额 */
    private BigDecimal amount;
    /** 实际支出 */
    private BigDecimal actual;
    /** 执行率(%) = 实际/预算 × 100 */
    private BigDecimal rate;
    /** 是否超支 */
    private Boolean overrun;
}
