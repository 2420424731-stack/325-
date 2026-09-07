package com.family.finance.vo;

import lombok.Data;

/**
 * 环比/同比分析结果（设计文档 8.2 compare）
 */
@Data
public class CompareVO {

    /** 分析月份, 如 2026-09 */
    private String month;

    private StatPointVO income;
    private StatPointVO expense;
    private StatPointVO balance;

    /** 一句话结论（展示用） */
    private String conclusion;
}
