package com.family.finance.vo;

import lombok.Data;

/**
 * 月度分析报告（纯文本，逐行展示）
 */
@Data
public class AnalysisReportVO {

    /** 分析月份, 如 2026-09 */
    private String month;

    /** 报告全文（\n 分行） */
    private String text;
}
