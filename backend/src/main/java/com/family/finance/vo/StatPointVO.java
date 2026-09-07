package com.family.finance.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 单指标对比点：本期值 + 环比(上月) + 同比(去年同月)
 */
@Data
public class StatPointVO {

    /** 本期值 */
    private BigDecimal current;
    /** 上月值（环比基准） */
    private BigDecimal mom;
    /** 去年同月值（同比基准） */
    private BigDecimal yoy;
    /** 环比变化率(%)，正=增长负=下降；基准为 0 时返回 null */
    private BigDecimal momPct;
    /** 同比变化率(%)，同上 */
    private BigDecimal yoyPct;
}
