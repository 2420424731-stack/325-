package com.family.finance.service;

import com.family.finance.vo.CategoryStatVO;
import com.family.finance.vo.NameStatVO;
import com.family.finance.vo.StatsOverviewVO;
import com.family.finance.vo.TrendPointVO;

import java.util.List;

/**
 * 统计服务（客观汇总，见设计文档 8.1）。家庭流水量小，采用内存聚合实现，
 * 口径与文档 5.4 SQL 思路一致，便于按维度扩展分析接口
 */
public interface StatsService {

    /** 月度/年度收支汇总（year+month，month 空=整年） */
    StatsOverviewVO overview(Integer year, Integer month);

    /** 近 N 个月收支趋势（含无数据月份补 0） */
    List<TrendPointVO> trend(int months);

    /** 分类汇总（type 1/2 必填） */
    List<CategoryStatVO> category(Integer year, Integer month, Integer type);

    /** 按成员汇总（type 空=全部） */
    List<NameStatVO> member(Integer year, Integer month, Integer type);

    /** 商家 Top N（type 默认 2 支出；categoryId 可选，含子树） */
    List<NameStatVO> merchant(Integer year, Integer month, Integer type, Long categoryId, int topN);

    /** 片区分布（type 默认 2 支出；categoryId 可选） */
    List<NameStatVO> region(Integer year, Integer month, Integer type, Long categoryId);

    /** 标签汇总（tag 空=各标签分别 Top N；指定 tag 则返回该标签一条汇总） */
    List<NameStatVO> tags(Integer year, Integer month, Integer type, String tag);
}
