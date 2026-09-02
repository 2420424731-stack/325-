package com.family.finance.service;

import com.family.finance.vo.AnomalyVO;
import com.family.finance.vo.AnalysisReportVO;
import com.family.finance.vo.CompareVO;

import java.util.List;

/**
 * 分析服务（发现问题并解释，见设计文档 8.2）。
 * 与「统计」严格区分：统计做客观汇总，分析基于规则对汇总结果做判断与解释。
 * month 参数格式 "yyyy-MM"，为空表示当月。
 */
public interface AnalysisService {

    /** 环比/同比：本月 vs 上月 vs 去年同月（收入/支出/结余） */
    CompareVO compare(String month);

    /** 月度异常与关注项列表（7 条规则依次判定，见实现注释） */
    List<AnomalyVO> anomalies(String month);

    /** 一键生成月度分析报告（文本） */
    AnalysisReportVO report(String month);
}
