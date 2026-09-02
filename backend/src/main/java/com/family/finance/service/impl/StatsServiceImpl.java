package com.family.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.family.finance.common.BizException;
import com.family.finance.entity.Category;
import com.family.finance.entity.FamilyMember;
import com.family.finance.entity.Transaction;
import com.family.finance.mapper.CategoryMapper;
import com.family.finance.mapper.FamilyMemberMapper;
import com.family.finance.mapper.TransactionMapper;
import com.family.finance.service.FamilyScopeService;
import com.family.finance.service.StatsService;
import com.family.finance.vo.CategoryStatVO;
import com.family.finance.vo.NameStatVO;
import com.family.finance.vo.StatsOverviewVO;
import com.family.finance.vo.TrendPointVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 统计服务实现。
 * 口径与设计文档 5.4「统计查询 SQL 思路」一致：按 biz_date 区间取数，
 * 家庭流水量小故内存聚合；trend 在 Java 端补齐无数据月份（等价 SQL 的 DATE_FORMAT 分组后补 0）。
 */
@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private final TransactionMapper transactionMapper;
    private final CategoryMapper categoryMapper;
    private final FamilyMemberMapper memberMapper;
    private final FamilyScopeService scope;

    @Override
    public StatsOverviewVO overview(Integer year, Integer month) {
        Range r = resolveRange(year, month);
        List<Transaction> list = listByRange(r);
        StatsOverviewVO vo = new StatsOverviewVO();
        vo.setYear(r.start.getYear());
        vo.setMonth(r.month);
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        long incomeCount = 0;
        long expenseCount = 0;
        for (Transaction t : list) {
            if (t.getType() == 1) {
                income = income.add(t.getAmount());
                incomeCount++;
            } else {
                expense = expense.add(t.getAmount());
                expenseCount++;
            }
        }
        vo.setIncome(income);
        vo.setExpense(expense);
        vo.setBalance(income.subtract(expense));
        vo.setIncomeCount(incomeCount);
        vo.setExpenseCount(expenseCount);
        return vo;
    }

    @Override
    public List<TrendPointVO> trend(int months) {
        int n = Math.max(months, 2);
        // 从 months 个月前（含当月）的连续月份，主键按 yyyy-MM 字符串天然有序
        YearMonth startYm = YearMonth.now().minusMonths(n - 1L);
        Range r = new Range(startYm.atDay(1), YearMonth.now().atEndOfMonth(), null);
        Map<String, TrendPointVO> byMonth = new HashMap<>();
        for (Transaction t : listByRange(r)) {
            String key = YearMonth.from(t.getBizDate()).toString();
            TrendPointVO p = byMonth.computeIfAbsent(key, k -> {
                TrendPointVO v = new TrendPointVO();
                v.setMonth(k);
                v.setIncome(BigDecimal.ZERO);
                v.setExpense(BigDecimal.ZERO);
                return v;
            });
            if (t.getType() == 1) {
                p.setIncome(p.getIncome().add(t.getAmount()));
            } else {
                p.setExpense(p.getExpense().add(t.getAmount()));
            }
        }
        List<TrendPointVO> points = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            YearMonth ym = startYm.plusMonths(i);
            points.add(byMonth.getOrDefault(ym.toString(), pointOf(ym)));
        }
        return points;
    }

    @Override
    public List<CategoryStatVO> category(Integer year, Integer month, Integer type) {
        validateType(type);
        Range r = resolveRange(year, month);
        Long familyId = scope.familyId();
        Map<Long, String> names = categoryNameMap(familyId);

        Map<Long, CategoryStatVO> statMap = new LinkedHashMap<>();
        for (Transaction t : listByRange(r)) {
            if (!Objects.equals(t.getType(), type)) {
                continue;
            }
            CategoryStatVO s = statMap.computeIfAbsent(t.getCategoryId(), k -> {
                CategoryStatVO v = new CategoryStatVO();
                v.setCategoryId(k);
                v.setCategoryName(names.getOrDefault(k, "未知分类"));
                v.setType(type);
                v.setTotal(BigDecimal.ZERO);
                v.setCount(0L);
                return v;
            });
            s.setTotal(s.getTotal().add(t.getAmount()));
            s.setCount(s.getCount() + 1);
        }
        return statMap.values().stream()
                .sorted(Comparator.comparing(CategoryStatVO::getTotal).reversed())
                .toList();
    }

    @Override
    public List<NameStatVO> member(Integer year, Integer month, Integer type) {
        Range r = resolveRange(year, month);
        Long familyId = scope.familyId();
        Map<Long, String> memberNames = new HashMap<>();
        for (FamilyMember m : memberMapper.selectList(
                new LambdaQueryWrapper<FamilyMember>().eq(FamilyMember::getFamilyId, familyId))) {
            memberNames.put(m.getId(), m.getName());
        }
        List<NameStatVO> result = aggregateBy(t -> {
            Long memberId = t.getMemberId();
            return memberId == null ? null : memberNames.get(memberId);
        }, listByRange(r), type);
        result.sort(Comparator.comparing(NameStatVO::getTotal).reversed());
        return result;
    }

    @Override
    public List<NameStatVO> merchant(Integer year, Integer month, Integer type, Long categoryId, int topN) {
        Range r = resolveRange(year, month);
        List<Transaction> list = filterByCategory(listByRange(r), categoryId);
        List<NameStatVO> result = aggregateBy(t -> t.getMerchant(), list, typeOf(type, 2));
        result.sort(Comparator.comparing(NameStatVO::getTotal).reversed());
        return result.stream().limit(Math.max(topN, 1)).toList();
    }

    @Override
    public List<NameStatVO> region(Integer year, Integer month, Integer type, Long categoryId) {
        Range r = resolveRange(year, month);
        List<Transaction> list = filterByCategory(listByRange(r), categoryId);
        List<NameStatVO> result = aggregateBy(t -> t.getRegion(), list, typeOf(type, 2));
        result.sort(Comparator.comparing(NameStatVO::getTotal).reversed());
        return result;
    }

    @Override
    public List<NameStatVO> tags(Integer year, Integer month, Integer type, String tag) {
        Range r = resolveRange(year, month);
        List<Transaction> list = listByRange(r);
        // 指定 tag：仅统计含该标签的记录（对应 5.4 SQL ⑥ FIND_IN_SET 语义）
        if (StringUtils.hasText(tag)) {
            String target = tag.trim();
            List<NameStatVO> one = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;
            long cnt = 0;
            for (Transaction t : list) {
                if (matchType(t, typeOf(type, null)) && containsTag(t.getTags(), target)) {
                    total = total.add(t.getAmount());
                    cnt++;
                }
            }
            if (cnt > 0) {
                NameStatVO v = new NameStatVO();
                v.setName(target);
                v.setTotal(total);
                v.setCount(cnt);
                one.add(v);
            }
            return one;
        }
        // 未指定：逐标签聚合 Top N
        Integer targetType = typeOf(type, null);
        Map<String, NameStatVO> statMap = new LinkedHashMap<>();
        for (Transaction t : list) {
            if (!matchType(t, targetType) || !StringUtils.hasText(t.getTags())) {
                continue;
            }
            for (String oneTag : t.getTags().split("[,，]")) {
                String key = oneTag.trim();
                if (key.isEmpty()) {
                    continue;
                }
                NameStatVO s = statMap.computeIfAbsent(key, k -> {
                    NameStatVO v = new NameStatVO();
                    v.setName(k);
                    v.setTotal(BigDecimal.ZERO);
                    v.setCount(0L);
                    return v;
                });
                s.setTotal(s.getTotal().add(t.getAmount()));
                s.setCount(s.getCount() + 1);
            }
        }
        List<NameStatVO> result = new ArrayList<>(statMap.values());
        result.sort(Comparator.comparing(NameStatVO::getTotal).reversed());
        return result.stream().limit(10).toList();
    }

    // ---------- 私有辅助 ----------

    /** 时间范围：month 为空 → 整年 */
    private record Range(LocalDate start, LocalDate end, Integer month) {
    }

    private Range resolveRange(Integer year, Integer month) {
        int y = year == null ? LocalDate.now().getYear() : year;
        if (y < 2000 || y > 2100) {
            throw new BizException(400, "year 取值异常");
        }
        if (month == null) {
            return new Range(LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31), null);
        }
        if (month < 1 || month > 12) {
            throw new BizException(400, "month 必须为 1~12");
        }
        YearMonth ym = YearMonth.of(y, month);
        return new Range(ym.atDay(1), ym.atEndOfMonth(), month);
    }

    /** 区间内全部未删除流水（family 隔离） */
    private List<Transaction> listByRange(Range r) {
        return transactionMapper.selectList(
                new LambdaQueryWrapper<Transaction>()
                        .eq(Transaction::getFamilyId, scope.familyId())
                        .ge(Transaction::getBizDate, r.start)
                        .le(Transaction::getBizDate, r.end)
                        .orderByAsc(Transaction::getBizDate));
    }

    private TrendPointVO pointOf(YearMonth ym) {
        TrendPointVO v = new TrendPointVO();
        v.setMonth(ym.toString());
        v.setIncome(BigDecimal.ZERO);
        v.setExpense(BigDecimal.ZERO);
        return v;
    }

    /** type 为空时的兜底值 */
    private Integer typeOf(Integer type, Integer fallback) {
        if (type == null || (type != 1 && type != 2)) {
            if (fallback == null) {
                return null;
            }
            return fallback;
        }
        return type;
    }

    private boolean matchType(Transaction t, Integer type) {
        return type == null || Objects.equals(t.getType(), type);
    }

    private void validateType(Integer type) {
        if (type == null || (type != 1 && type != 2)) {
            throw new BizException(400, "type 必须为 1(收入)或 2(支出)");
        }
    }

    private boolean containsTag(String tags, String target) {
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

    /** categoryId 过滤（含其子孙分类） */
    private List<Transaction> filterByCategory(List<Transaction> list, Long categoryId) {
        if (categoryId == null) {
            return list;
        }
        List<Category> all = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().eq(Category::getFamilyId, scope.familyId()));
        List<Long> ids = new ArrayList<>();
        collectSubTree(all, categoryId, ids);
        return list.stream().filter(t -> ids.contains(t.getCategoryId())).toList();
    }

    private void collectSubTree(List<Category> all, Long rootId, List<Long> out) {
        out.add(rootId);
        for (Category c : all) {
            if (Objects.equals(c.getParentId(), rootId)) {
                collectSubTree(all, c.getId(), out);
            }
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

    /** 按「维度提取函数」聚合金额与笔数；提取结果为 null（如商家为空）的记录跳过 */
    private List<NameStatVO> aggregateBy(java.util.function.Function<Transaction, String> dimFn,
                                         List<Transaction> list, Integer type) {
        Map<String, NameStatVO> statMap = new LinkedHashMap<>();
        for (Transaction t : list) {
            if (!matchType(t, type)) {
                continue;
            }
            String dim = dimFn.apply(t);
            if (!StringUtils.hasText(dim)) {
                continue;
            }
            NameStatVO s = statMap.computeIfAbsent(dim, k -> {
                NameStatVO v = new NameStatVO();
                v.setName(k);
                v.setTotal(BigDecimal.ZERO);
                v.setCount(0L);
                return v;
            });
            s.setTotal(s.getTotal().add(t.getAmount()));
            s.setCount(s.getCount() + 1);
        }
        return new ArrayList<>(statMap.values());
    }
}
