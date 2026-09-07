package com.family.finance.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 预算视图：实体 + 分类名（categoryId 空=家庭总预算）
 */
@Data
public class BudgetVO {

    private Long id;
    /** 预算分类 id（空=家庭总预算） */
    private Long categoryId;
    /** 分类名（家庭总预算为「家庭总支出」） */
    private String categoryName;
    /** 预算月份, 如 2026-09 */
    private String budgetMonth;
    /** 预算金额 */
    private BigDecimal amount;
}
