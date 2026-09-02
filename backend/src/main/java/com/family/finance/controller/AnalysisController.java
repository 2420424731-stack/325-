package com.family.finance.controller;

import com.family.finance.common.Result;
import com.family.finance.service.AnalysisService;
import com.family.finance.vo.AnomalyVO;
import com.family.finance.vo.AnalysisReportVO;
import com.family.finance.vo.CompareVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分析接口（发现问题并解释，设计文档 8.2）。month 格式 yyyy-MM，空=当月
 */
@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    /** 环比/同比（vs 上月 / vs 去年同月） */
    @GetMapping("/compare")
    public Result<CompareVO> compare(@RequestParam(required = false) String month) {
        return Result.ok(analysisService.compare(month));
    }

    /** 异常与关注项列表（R1~R7 规则） */
    @GetMapping("/anomalies")
    public Result<List<AnomalyVO>> anomalies(@RequestParam(required = false) String month) {
        return Result.ok(analysisService.anomalies(month));
    }

    /** 一键生成月度分析报告（文本） */
    @GetMapping("/report")
    public Result<AnalysisReportVO> report(@RequestParam(required = false) String month) {
        return Result.ok(analysisService.report(month));
    }
}
