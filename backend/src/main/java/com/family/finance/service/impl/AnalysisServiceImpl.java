package com.family.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.family.finance.common.BizException;
import com.family.finance.entity.Budget;
import com.family.finance.entity.Category;
import com.family.finance.entity.Transaction;
import com.family.finance.mapper.BudgetMapper;
import com.family.finance.mapper.CategoryMapper;
import com.family.finance.mapper.TransactionMapper;
import com.family.finance.service.AnalysisService;
import com.family.finance.service.FamilyScopeService;
import com.family.finance.vo.AnomalyVO;
import com.family.finance.vo.AnalysisReportVO;
import com.family.finance.vo.CompareVO;
import com.family.finance.vo.StatPointVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 分析服务实现：在统计结果之上运行规则，输出"问题+解释+建议"。
 * 规则与设计文档 8.2 一一对应，每个规则一个方法，答辩可现场调整阈值后重新生成报告：
 *   R1 月度异常支出：本月支出 > 近 11 个月自然月均 × 130%
 *   R2 分类异常波动：某分类本月 > 该分类历史月均 × 150% 且绝对差 > 200 元
 *   R3 预算超支：实际支出 > 预算金额（budget 表无数据时自动跳过）
 *   R4 连续增长：支出连续 3 个月环比上升
 *   R5 收入结构：单分类收入占比 > 80%
 *   R6 外部餐饮专项：餐饮子树 Top 商家 + 片区（参考卡片）
 *   R7 人情往来专项：礼尚往来 年度总额/笔数/占比（参考卡片）
 */
@Service
@RequiredArgsConstructor
public class AnalysisServiceImpl implements AnalysisService {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    /** 历史窗口：不含本月的前 11 个自然月（设计文档 5.4⑦） */
    private static final int HISTORY_MONTHS = 11;
    /** R1 阈值：超月均 30% 即预警 */
    private static final BigDecimal R1_RATIO = new BigDecimal("1.30");
    /** R2 阈值：超分类月均 50% */
    private static final BigDecimal R2_RATIO = new BigDecimal("1.50");
    /** R2 绝对差下限（元） */
    private static final BigDecimal R2_MIN_DIFF = new BigDecimal("200");
    /** R5 收入集中阈值 */
    private static final BigDecimal R5_RATIO = new BigDecimal("0.80");

    private final TransactionMapper transactionMapper;
    private final CategoryMapper categoryMapper;
    private final BudgetMapper budgetMapper;
    private final FamilyScopeService scope;

    @Override
    public CompareVO compare(String month) {
        YearMonth ym = resolveMonth(month);
        List<Transaction> list = loadRange(ym.minusMonths(12), ym);
        Map<YearMonth, List<Transaction>> byMonth = groupByMonth(list);

        CompareVO vo = new CompareVO();
        vo.setMonth(ym.toString());
        vo.setIncome(buildPoint(byMonth, ym, ym.minusMonths(1), ym.minusYears(1), 1));
        vo.setExpense(buildPoint(byMonth, ym, ym.minusMonths(1), ym.minusYears(1), 2));
        vo.setBalance(buildPoint(byMonth, ym, ym.minusMonths(1), ym.minusYears(1), null));
        vo.setConclusion(buildConclusion(vo));
        return vo;
    }

    @Override
    public List<AnomalyVO> anomalies(String month) {
        YearMonth ym = resolveMonth(month);
        Long familyId = scope.familyId();
        List<Transaction> history = loadRange(ym.minusMonths(HISTORY_MONTHS), ym);
        List<Transaction> currentMonth = filterMonth(history, ym);
        Map<YearMonth, List<Transaction>> byMonth = groupByMonth(history);

        List<AnomalyVO> result = new ArrayList<>();
        result.addAll(rule1(ym, byMonth));
        result.addAll(rule2(familyId, ym, byMonth));
        result.addAll(rule3(familyId, ym, currentMonth));
        rule4(ym, byMonth).ifPresent(result::add);
        rule5(ym, currentMonth).ifPresent(result::add);
        rule6(familyId, ym, currentMonth).ifPresent(result::add);
        rule7(familyId, ym, byMonth).ifPresent(result::add);
        return result;
    }

    @Override
    public AnalysisReportVO report(String month) {
        YearMonth ym = resolveMonth(month);
        List<AnomalyVO> items = anomalies(ym.toString());
        CompareVO cmp = compare(ym.toString());
        DecimalFormat money = new DecimalFormat("#,##0.00");

        StringBuilder sb = new StringBuilder();
        sb.append("📊 ").append(ym.getYear()).append(" 年 ").append(ym.getMonthValue())
                .append(" 月分析报告\n\n");

        // 一、概况
        sb.append("一、本月概况\n");
        sb.append("  收入 ").append(money.format(cmp.getIncome().getCurrent()))
                .append(" 元，支出 ").append(money.format(cmp.getExpense().getCurrent()))
                .append(" 元，结余 ").append(money.format(cmp.getBalance().getCurrent()))
                .append(" 元。").append(cmp.getConclusion()).append("\n");

        // 二、发现的问题（danger/warning）
        List<AnomalyVO> problems = items.stream()
                .filter(a -> !"info".equals(a.getLevel())).toList();
        sb.append("二、发现的问题\n");
        if (problems.isEmpty()) {
            sb.append("  本月未发现明显异常：支出在历史水平内，预算与趋势平稳。\n");
        } else {
            int i = 1;
            for (AnomalyVO a : problems) {
                sb.append("  ").append(i++).append(". ").append(a.getTitle())
                        .append("：").append(a.getDescription()).append("\n");
            }
        }

        // 三、参考信息（info 卡片）
        List<AnomalyVO> refs = items.stream()
                .filter(a -> "info".equals(a.getLevel())).toList();
        if (!refs.isEmpty()) {
            sb.append("三、参考信息\n");
            for (AnomalyVO a : refs) {
                sb.append("  · ").append(a.getDescription()).append("\n");
            }
        }

        // 四、建议
        List<AnomalyVO> suggest = items.stream()
                .filter(a -> StringUtils.hasText(a.getSuggestion()))
                .filter(a -> !"info".equals(a.getLevel()))
                .limit(3).toList();
        sb.append("四、建议\n");
        if (suggest.isEmpty()) {
            sb.append("  保持现有记账习惯，建议每月初复核上月分类占比与下月预算。\n");
        } else {
            for (AnomalyVO a : suggest) {
                sb.append("  · ").append(a.getSuggestion()).append("\n");
            }
        }
        return textReport(ym, sb.toString());
    }

    // ================= 规则实现 =================

    /** R1 月度异常支出：本月支出 > 近 11 个自然月均 × 130%（有效月 ≥ 6 才判定） */
    private List<AnomalyVO> rule1(YearMonth ym, Map<YearMonth, List<Transaction>> byMonth) {
        BigDecimal current = monthExpense(byMonth, ym);
        BigDecimal windowSum = BigDecimal.ZERO;
        int activeMonths = 0;
        for (int i = 1; i <= HISTORY_MONTHS; i++) {
            YearMonth h = ym.minusMonths(i);
            BigDecimal e = monthExpense(byMonth, h);
            windowSum = windowSum.add(e);
            if (e.compareTo(BigDecimal.ZERO) > 0) {
                activeMonths++;
            }
        }
        if (current.signum() == 0 || activeMonths < 6) {
            return List.of();
        }
        BigDecimal avg = windowSum.divide(new BigDecimal(HISTORY_MONTHS), 2, RoundingMode.HALF_UP);
        BigDecimal threshold = avg.multiply(R1_RATIO);
        if (current.compareTo(threshold) <= 0) {
            return List.of();
        }
        AnomalyVO a = new AnomalyVO();
        a.setRuleCode("R1");
        a.setTitle("本月支出超出历史月均");
        a.setLevel("danger");
        a.setDimension("总支出");
        a.setCurrent(current);
        a.setBaseline(avg);
        a.setExceedPct(pctGap(current, avg));
        a.setDescription(String.format("本月支出 %s 元，高于近 %d 个月月均 %s 元的 %d%% 预警线，超出 %.0f%%。",
                money(current), HISTORY_MONTHS, money(avg), 130, a.getExceedPct()));
        a.setSuggestion("建议查看本月支出明细（尤其单笔大额），对照下月预算进行压缩。");
        return List.of(a);
    }

    /** R2 分类异常波动：某分类本月 > 该分类历史月均 × 150% 且绝对差 > 200 元（有记录月 ≥ 3） */
    private List<AnomalyVO> rule2(Long familyId, YearMonth ym, Map<YearMonth, List<Transaction>> byMonth) {
        List<AnomalyVO> result = new ArrayList<>();
        Map<Long, String> catName = categoryNameMap(familyId);
        // 本月按分类汇总
        Map<Long, BigDecimal> curByCat = new HashMap<>();
        for (Transaction t : filterMonth(all(byMonth), ym)) {
            if (t.getType() == 2) {
                curByCat.merge(t.getCategoryId(), t.getAmount(), BigDecimal::add);
            }
        }
        // 历史窗口按分类汇总（当月不算）
        Map<Long, MonthStat> hist = new HashMap<>();
        for (Map.Entry<YearMonth, List<Transaction>> e : byMonth.entrySet()) {
            if (!e.getKey().isBefore(ym)) {
                continue;
            }
            Map<Long, BigDecimal> perCat = new HashMap<>();
            for (Transaction t : e.getValue()) {
                if (t.getType() == 2) {
                    perCat.merge(t.getCategoryId(), t.getAmount(), BigDecimal::add);
                }
            }
            for (Map.Entry<Long, BigDecimal> c : perCat.entrySet()) {
                hist.computeIfAbsent(c.getKey(), k -> new MonthStat()).add(c.getValue());
            }
        }
        for (Map.Entry<Long, BigDecimal> cur : curByCat.entrySet()) {
            MonthStat ms = hist.get(cur.getKey());
            if (ms == null || ms.months < 3) {
                continue;
            }
            BigDecimal avg = ms.sum.divide(BigDecimal.valueOf(ms.months), 2, RoundingMode.HALF_UP);
            BigDecimal diff = cur.getValue().subtract(avg);
            if (cur.getValue().compareTo(avg.multiply(R2_RATIO)) > 0
                    && diff.compareTo(R2_MIN_DIFF) > 0) {
                AnomalyVO a = new AnomalyVO();
                a.setRuleCode("R2");
                a.setTitle("分类支出异常增长");
                a.setLevel("warning");
                a.setDimension("分类：" + catName.getOrDefault(cur.getKey(), "未知分类"));
                a.setCurrent(cur.getValue());
                a.setBaseline(avg);
                a.setExceedPct(pctGap(cur.getValue(), avg));
                a.setDescription(String.format("「%s」本月支出 %s 元，超该分类历史月均 %s 元 %d%% 且金额差超过 %s 元，属异常波动。",
                        a.getDimension(), money(cur.getValue()), money(avg), 150, money(R2_MIN_DIFF)));
                a.setSuggestion("点击钻取该分类流水，核对是否为一次性大额或漏记补偿。");
                a.setDrillCategoryId(cur.getKey());
                result.add(a);
            }
        }
        result.sort(Comparator.comparing(AnomalyVO::getExceedPct, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return result;
    }

    /** R3 预算超支：实际支出 > 预算金额（budget 表无数据自动跳过） */
    private List<AnomalyVO> rule3(Long familyId, YearMonth ym, List<Transaction> currentMonth) {
        List<Budget> budgets = budgetMapper.selectList(
                new LambdaQueryWrapper<Budget>()
                        .eq(Budget::getFamilyId, familyId)
                        .eq(Budget::getBudgetMonth, ym.toString()));
        if (budgets.isEmpty()) {
            return List.of();
        }
        // 家庭级预算（categoryId 空）与分类预算并存：家庭预算与实际=全月总支出
        BigDecimal monthTotal = sumOf(currentMonth, 2);
        List<Category> allCats = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().eq(Category::getFamilyId, familyId));
        Map<Long, String> catName = new HashMap<>();
        for (Category c : allCats) {
            catName.put(c.getId(), c.getName());
        }
        List<AnomalyVO> result = new ArrayList<>();
        for (Budget b : budgets) {
            BigDecimal actual;
            String dimension;
            Long drillId = null;
            if (b.getCategoryId() == null) {
                actual = monthTotal;
                dimension = "家庭总支出";
            } else {
                // 分类预算按含子孙分类汇总
                List<Long> sub = new ArrayList<>();
                collectSubTree(allCats, b.getCategoryId(), sub);
                BigDecimal sum = BigDecimal.ZERO;
                for (Transaction t : currentMonth) {
                    if (t.getType() == 2 && sub.contains(t.getCategoryId())) {
                        sum = sum.add(t.getAmount());
                    }
                }
                actual = sum;
                dimension = "分类：" + catName.getOrDefault(b.getCategoryId(), "未知");
                drillId = b.getCategoryId();
            }
            if (actual.compareTo(b.getAmount()) <= 0) {
                continue;
            }
            AnomalyVO a = new AnomalyVO();
            a.setRuleCode("R3");
            a.setTitle("预算超支");
            a.setLevel("danger");
            a.setDimension(dimension);
            a.setCurrent(actual);
            a.setBaseline(b.getAmount());
            a.setExceedPct(actual.subtract(b.getAmount())
                    .multiply(new BigDecimal("100")).divide(b.getAmount(), 1, RoundingMode.HALF_UP));
            a.setDescription(String.format("%s已支出 %s 元，超出预算 %s 元的 %.0f%%。",
                    dimension, money(actual), money(b.getAmount()), a.getExceedPct()));
            a.setSuggestion("下月可将该项预算上调或主动压缩，避免超支常态化。");
            a.setDrillCategoryId(drillId);
            result.add(a);
        }
        return result;
    }

    /** R4 连续增长：本月、上月、上上月支出连续环比上升 */
    private java.util.Optional<AnomalyVO> rule4(YearMonth ym, Map<YearMonth, List<Transaction>> byMonth) {
        BigDecimal cur = monthExpense(byMonth, ym);
        BigDecimal prev = monthExpense(byMonth, ym.minusMonths(1));
        BigDecimal prev2 = monthExpense(byMonth, ym.minusMonths(2));
        if (cur.compareTo(prev) > 0 && prev.compareTo(prev2) > 0) {
            AnomalyVO a = new AnomalyVO();
            a.setRuleCode("R4");
            a.setTitle("支出连续三个月增长");
            a.setLevel("warning");
            a.setDimension("总支出");
            a.setCurrent(cur);
            a.setBaseline(prev);
            a.setDescription(String.format("支出已连续 3 个月环比上升：%s → %s → %s 元，增长趋势明显。",
                    money(prev2), money(prev), money(cur)));
            a.setSuggestion("若属季节性消费可观察下月回落；否则建议设置月度预算总额并逐周核对。");
            return java.util.Optional.of(a);
        }
        return java.util.Optional.empty();
    }

    /** R5 收入结构：本月单分类收入占比 > 80% */
    private java.util.Optional<AnomalyVO> rule5(YearMonth ym, List<Transaction> currentMonth) {
        BigDecimal total = sumOf(currentMonth, 1);
        if (total.signum() == 0) {
            return java.util.Optional.empty();
        }
        Map<Long, BigDecimal> byCat = new LinkedHashMap<>();
        for (Transaction t : currentMonth) {
            if (t.getType() == 1) {
                byCat.merge(t.getCategoryId(), t.getAmount(), BigDecimal::add);
            }
        }
        String topName = null;
        BigDecimal topSum = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal> e : byCat.entrySet()) {
            if (e.getValue().compareTo(topSum) > 0) {
                topSum = e.getValue();
                Category c = categoryMapper.selectById(e.getKey());
                topName = c == null ? "未知分类" : c.getName();
            }
        }
        BigDecimal ratio = topSum.divide(total, 4, RoundingMode.HALF_UP);
        if (topName == null || ratio.compareTo(R5_RATIO) <= 0) {
            return java.util.Optional.empty();
        }
        AnomalyVO a = new AnomalyVO();
        a.setRuleCode("R5");
        a.setTitle("收入来源集中");
        a.setLevel("info");
        a.setDimension("收入结构");
        a.setCurrent(topSum);
        a.setBaseline(total);
        a.setExceedPct(ratio.movePointRight(2).setScale(0, RoundingMode.HALF_UP));
        a.setDescription(String.format("本月收入中「%s」占 %.0f%%，来源较单一，需关注收入中断风险。",
                topName, a.getExceedPct()));
        a.setSuggestion("建议拓宽收入来源（经营/投资/兼职），增强家庭抗风险能力。");
        return java.util.Optional.of(a);
    }

    /** R6 外部餐饮专项：餐饮子树本月支出、Top 商家、集中片区（参考卡片） */
    private java.util.Optional<AnomalyVO> rule6(Long familyId, YearMonth ym, List<Transaction> currentMonth) {
        List<Category> allCats = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().eq(Category::getFamilyId, familyId));
        // 顶层含"餐饮"的分类作为餐饮根
        Long rootId = allCats.stream()
                .filter(c -> c.getParentId() == 0L && c.getName() != null && c.getName().contains("餐饮"))
                .map(Category::getId).findFirst().orElse(null);
        if (rootId == null) {
            return java.util.Optional.empty();
        }
        List<Long> diningIds = new ArrayList<>();
        collectSubTree(allCats, rootId, diningIds);
        BigDecimal total = BigDecimal.ZERO;
        Map<String, BigDecimal> merchantSum = new HashMap<>();
        Map<String, Long> merchantCnt = new HashMap<>();
        Map<String, BigDecimal> regionSum = new HashMap<>();
        for (Transaction t : currentMonth) {
            if (t.getType() == 2 && diningIds.contains(t.getCategoryId())) {
                total = total.add(t.getAmount());
                if (StringUtils.hasText(t.getMerchant())) {
                    merchantSum.merge(t.getMerchant(), t.getAmount(), BigDecimal::add);
                    merchantCnt.merge(t.getMerchant(), 1L, Long::sum);
                }
                if (StringUtils.hasText(t.getRegion())) {
                    regionSum.merge(t.getRegion(), t.getAmount(), BigDecimal::add);
                }
            }
        }
        if (total.signum() == 0) {
            return java.util.Optional.empty();
        }
        String topMerchant = merchantSum.entrySet().stream()
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        String topRegion = regionSum.entrySet().stream()
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        AnomalyVO a = new AnomalyVO();
        a.setRuleCode("R6");
        a.setTitle("外部餐饮消费分析");
        a.setLevel("info");
        a.setDimension("外部餐饮");
        a.setCurrent(total);
        a.setExceedPct(null);
        StringBuilder desc = new StringBuilder("本月外部餐饮共支出 ")
                .append(money(total)).append(" 元");
        if (topMerchant != null) {
            BigDecimal mTotal = merchantSum.get(topMerchant);
            desc.append("，主要商家「").append(topMerchant).append("」")
                    .append(money(mTotal)).append(" 元（").append(merchantCnt.get(topMerchant)).append(" 笔，占 ")
                    .append(mTotal.multiply(new BigDecimal("100")).divide(total, 0, RoundingMode.HALF_UP))
                    .append("%）");
        }
        if (topRegion != null) {
            desc.append("，消费集中在「").append(topRegion).append("」片区");
        }
        desc.append("。");
        a.setDescription(desc.toString());
        a.setSuggestion("关注高频商家与集中片区，必要时对比团购/堂食价格。");
        a.setDrillCategoryId(rootId);
        return java.util.Optional.of(a);
    }

    /** R7 人情往来专项：礼尚往来年度总额/笔数/占比（参考卡片） */
    private java.util.Optional<AnomalyVO> rule7(Long familyId, YearMonth ym, Map<YearMonth, List<Transaction>> byMonth) {
        YearMonth start = YearMonth.of(ym.getYear(), 1);
        BigDecimal total = BigDecimal.ZERO;
        long cnt = 0;
        List<Transaction> all = all(byMonth);
        for (Transaction t : all) {
            if (YearMonth.from(t.getBizDate()).isBefore(start)) {
                continue;
            }
            if (t.getType() == 2 && hasTag(t.getTags(), "礼尚往来")) {
                total = total.add(t.getAmount());
                cnt++;
            }
        }
        if (cnt == 0) {
            return java.util.Optional.empty();
        }
        BigDecimal yearExpense = sumOf(all, 2, start, YearMonth.of(ym.getYear(), 12));
        AnomalyVO a = new AnomalyVO();
        a.setRuleCode("R7");
        a.setTitle("人情往来支出");
        a.setLevel("info");
        a.setDimension("礼尚往来");
        a.setCurrent(total);
        a.setBaseline(yearExpense);
        a.setExceedPct(yearExpense.signum() == 0 ? null
                : total.multiply(new BigDecimal("100")).divide(yearExpense, 1, RoundingMode.HALF_UP));
        a.setDescription(String.format("%d 年「礼尚往来」标签共支出 %s 元（%d 笔）",
                ym.getYear(), money(total), cnt)
                + (yearExpense.signum() == 0 ? "。"
                : String.format("，占全年支出的 %.0f%%。", a.getExceedPct())));
        a.setSuggestion("对高频人情对象可提前规划预算，避免突击性大额支出。");
        a.setDrillTag("礼尚往来");
        return java.util.Optional.of(a);
    }

    // ================= 通用辅助 =================

    /** 单月对比点：值/环比/同比（type=null 表示结余=收入-支出） */
    private StatPointVO buildPoint(Map<YearMonth, List<Transaction>> byMonth,
                                   YearMonth curYm, YearMonth momYm, YearMonth yoyYm, Integer type) {
        StatPointVO p = new StatPointVO();
        p.setCurrent(valueOf(byMonth, curYm, type));
        p.setMom(valueOf(byMonth, momYm, type));
        p.setYoy(valueOf(byMonth, yoyYm, type));
        p.setMomPct(pctChange(p.getCurrent(), p.getMom()));
        p.setYoyPct(pctChange(p.getCurrent(), p.getYoy()));
        return p;
    }

    private BigDecimal valueOf(Map<YearMonth, List<Transaction>> byMonth, YearMonth ym, Integer type) {
        List<Transaction> list = byMonth.get(ym);
        if (list == null || list.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return type == null ? sumOf(list, 1).subtract(sumOf(list, 2)) : sumOf(list, type);
    }

    /** 一句结论：支出/收入环比同比（负=下降） */
    private String buildConclusion(CompareVO vo) {
        StatPointVO exp = vo.getExpense();
        StatPointVO inc = vo.getIncome();
        StringBuilder sb = new StringBuilder("支出");
        appendChange(sb, exp.getMomPct(), exp.getMom(), "上月");
        sb.append("，");
        appendChange(sb, exp.getYoyPct(), exp.getYoy(), "去年同月");
        sb.append("；收入");
        appendChange(sb, inc.getMomPct(), inc.getMom(), "上月");
        sb.append("。");
        return sb.toString();
    }

    private void appendChange(StringBuilder sb, BigDecimal pct, BigDecimal base, String period) {
        if (pct == null || base == null || base.signum() <= 0) {
            sb.append("较").append(period).append("无比较基准");
            return;
        }
        sb.append("较").append(period);
        if (pct.signum() >= 0) {
            sb.append("增长 ");
        } else {
            sb.append("下降 ");
        }
        sb.append(pct.abs().setScale(1, RoundingMode.HALF_UP)).append("%");
    }

    /** (current-baseline)/baseline×100（1 位小数；基准 ≤0 时比率无意义返回 null） */
    private BigDecimal pctGap(BigDecimal current, BigDecimal baseline) {
        if (baseline == null || baseline.signum() <= 0) {
            return null;
        }
        return current.subtract(baseline).multiply(new BigDecimal("100"))
                .divide(baseline, 1, RoundingMode.HALF_UP);
    }

    /** 变化率同 pctGap（基准 ≤0 返回 null，如结余由负转正不报百分比） */
    private BigDecimal pctChange(BigDecimal current, BigDecimal baseline) {
        if (baseline == null || baseline.signum() <= 0) {
            return null;
        }
        return current.subtract(baseline).multiply(new BigDecimal("100"))
                .divide(baseline, 1, RoundingMode.HALF_UP);
    }

    private BigDecimal sumOf(List<Transaction> list, Integer type) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Transaction t : list) {
            if (t.getType().equals(type)) {
                sum = sum.add(t.getAmount());
            }
        }
        return sum;
    }

    /** 按年份区间汇总（bizDate in [from,to]） */
    private BigDecimal sumOf(List<Transaction> list, Integer type, YearMonth from, YearMonth to) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Transaction t : list) {
            if (!t.getType().equals(type)) {
                continue;
            }
            YearMonth ym = YearMonth.from(t.getBizDate());
            if (!ym.isBefore(from) && !ym.isAfter(to)) {
                sum = sum.add(t.getAmount());
            }
        }
        return sum;
    }

    private BigDecimal monthExpense(Map<YearMonth, List<Transaction>> byMonth, YearMonth ym) {
        List<Transaction> list = byMonth.get(ym);
        return list == null ? BigDecimal.ZERO : sumOf(list, 2);
    }

    private List<Transaction> filterMonth(List<Transaction> list, YearMonth ym) {
        List<Transaction> out = new ArrayList<>();
        for (Transaction t : list) {
            if (YearMonth.from(t.getBizDate()).equals(ym)) {
                out.add(t);
            }
        }
        return out;
    }

    private List<Transaction> all(Map<YearMonth, List<Transaction>> byMonth) {
        List<Transaction> out = new ArrayList<>();
        for (List<Transaction> v : byMonth.values()) {
            out.addAll(v);
        }
        return out;
    }

    /** 区间加载流水（区间取整月，用 loadRange 一次查库后内存分月） */
    private List<Transaction> loadRange(YearMonth from, YearMonth to) {
        return transactionMapper.selectList(
                new LambdaQueryWrapper<Transaction>()
                        .eq(Transaction::getFamilyId, scope.familyId())
                        .ge(Transaction::getBizDate, from.atDay(1))
                        .le(Transaction::getBizDate, to.atEndOfMonth()));
    }

    private Map<YearMonth, List<Transaction>> groupByMonth(List<Transaction> list) {
        Map<YearMonth, List<Transaction>> map = new HashMap<>();
        for (Transaction t : list) {
            map.computeIfAbsent(YearMonth.from(t.getBizDate()), k -> new ArrayList<>()).add(t);
        }
        return map;
    }

    private YearMonth resolveMonth(String month) {
        if (!StringUtils.hasText(month)) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month.trim(), MONTH_FMT);
        } catch (DateTimeParseException e) {
            throw new BizException(400, "month 格式须为 yyyy-MM，如 2026-09");
        }
    }

    private Map<Long, String> categoryNameMap(Long familyId) {
        Map<Long, String> map = new HashMap<>();
        for (Category c : categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().eq(Category::getFamilyId, familyId))) {
            map.put(c.getId(), c.getName());
        }
        return map;
    }

    private void collectSubTree(List<Category> all, Long rootId, List<Long> out) {
        out.add(rootId);
        for (Category c : all) {
            if (Objects.equals(c.getParentId(), rootId)) {
                collectSubTree(all, c.getId(), out);
            }
        }
    }

    private boolean hasTag(String tags, String target) {
        if (!StringUtils.hasText(tags)) {
            return false;
        }
        for (String s : tags.split("[,，]")) {
            if (target.equals(s.trim())) {
                return true;
            }
        }
        return false;
    }

    private AnalysisReportVO textReport(YearMonth ym, String text) {
        AnalysisReportVO vo = new AnalysisReportVO();
        vo.setMonth(ym.toString());
        vo.setText(text);
        return vo;
    }

    private String money(BigDecimal v) {
        return new DecimalFormat("#,##0.00").format(v);
    }

    /** 分类历史统计（R2 用） */
    private static class MonthStat {
        BigDecimal sum = BigDecimal.ZERO;
        int months = 0;

        void add(BigDecimal v) {
            sum = sum.add(v);
            months++;
        }
    }
}
