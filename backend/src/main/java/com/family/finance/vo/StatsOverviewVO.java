package com.family.finance.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 收支汇总（月度/年度）。month 为空表示按整年汇总
 */
@Data
public class StatsOverviewVO {

    private Integer year;
    private Integer month;

    private BigDecimal income;
    private BigDecimal expense;
    private BigDecimal balance;
    private Long incomeCount;
    private Long expenseCount;
}
