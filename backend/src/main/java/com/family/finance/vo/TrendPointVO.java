package com.family.finance.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 月度趋势点（无数据月份补 0，保证折线图连续）
 */
@Data
public class TrendPointVO {

    /** 月份, 如 2026-09 */
    private String month;
    private BigDecimal income;
    private BigDecimal expense;
}
