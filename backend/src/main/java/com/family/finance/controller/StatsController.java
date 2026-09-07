package com.family.finance.controller;

import com.family.finance.common.Result;
import com.family.finance.service.StatsService;
import com.family.finance.vo.CategoryStatVO;
import com.family.finance.vo.NameStatVO;
import com.family.finance.vo.StatsOverviewVO;
import com.family.finance.vo.TrendPointVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 统计接口（客观汇总，设计文档 8.1）
 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    /** 月度/年度收支汇总（year+month，month 空=整年） */
    @GetMapping("/overview")
    public Result<StatsOverviewVO> overview(@RequestParam(required = false) Integer year,
                                            @RequestParam(required = false) Integer month) {
        return Result.ok(statsService.overview(year, month));
    }

    /** 近 N 个月收支趋势 */
    @GetMapping("/trend")
    public Result<List<TrendPointVO>> trend(@RequestParam(defaultValue = "12") int months) {
        return Result.ok(statsService.trend(months));
    }

    /** 分类汇总（饼图/柱状图数据） */
    @GetMapping("/category")
    public Result<List<CategoryStatVO>> category(@RequestParam(required = false) Integer year,
                                                 @RequestParam(required = false) Integer month,
                                                 @RequestParam Integer type) {
        return Result.ok(statsService.category(year, month, type));
    }

    /** 按成员汇总 */
    @GetMapping("/member")
    public Result<List<NameStatVO>> member(@RequestParam(required = false) Integer year,
                                           @RequestParam(required = false) Integer month,
                                           @RequestParam(required = false) Integer type) {
        return Result.ok(statsService.member(year, month, type));
    }

    /** 商家 Top N（默认支出 Top 10） */
    @GetMapping("/merchant")
    public Result<List<NameStatVO>> merchant(@RequestParam(required = false) Integer year,
                                             @RequestParam(required = false) Integer month,
                                             @RequestParam(required = false) Integer type,
                                             @RequestParam(required = false) Long categoryId,
                                             @RequestParam(defaultValue = "10") int topN) {
        return Result.ok(statsService.merchant(year, month, type, categoryId, topN));
    }

    /** 片区分布 */
    @GetMapping("/region")
    public Result<List<NameStatVO>> region(@RequestParam(required = false) Integer year,
                                           @RequestParam(required = false) Integer month,
                                           @RequestParam(required = false) Integer type,
                                           @RequestParam(required = false) Long categoryId) {
        return Result.ok(statsService.region(year, month, type, categoryId));
    }

    /** 标签汇总（tag 空=各标签 Top 10；指定 tag=单标签汇总） */
    @GetMapping("/tags")
    public Result<List<NameStatVO>> tags(@RequestParam(required = false) Integer year,
                                         @RequestParam(required = false) Integer month,
                                         @RequestParam(required = false) Integer type,
                                         @RequestParam(required = false) String tag) {
        return Result.ok(statsService.tags(year, month, type, tag));
    }
}
