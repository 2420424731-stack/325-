package com.family.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.family.finance.common.BizException;
import com.family.finance.dto.BudgetDTO;
import com.family.finance.entity.Budget;
import com.family.finance.entity.Category;
import com.family.finance.entity.Transaction;
import com.family.finance.mapper.BudgetMapper;
import com.family.finance.mapper.CategoryMapper;
import com.family.finance.mapper.TransactionMapper;
import com.family.finance.service.BudgetService;
import com.family.finance.service.FamilyScopeService;
import com.family.finance.vo.BudgetExecutionVO;
import com.family.finance.vo.BudgetVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 预算管理实现（设计文档 F11）。
 * - categoryId 空 = 家庭总预算；分类预算仅支持支出分类
 * - 执行率口径与设计文档 8.2 R3 一致：分类预算按「含子孙分类」汇总实际支出
 */
@Service
@RequiredArgsConstructor
public class BudgetServiceImpl implements BudgetService {

    private final BudgetMapper budgetMapper;
    private final CategoryMapper categoryMapper;
    private final TransactionMapper transactionMapper;
    private final FamilyScopeService scope;

    @Override
    public List<BudgetVO> list(String month) {
        YearMonth ym = resolveMonth(month);
        List<Budget> budgets = budgetMapper.selectList(
                new LambdaQueryWrapper<Budget>()
                        .eq(Budget::getFamilyId, scope.familyId())
                        .eq(Budget::getBudgetMonth, ym.toString())
                        .orderByAsc(Budget::getId));
        Map<Long, String> catName = categoryNameMap();
        List<BudgetVO> result = new ArrayList<>();
        for (Budget b : budgets) {
            result.add(toVO(b, b.getCategoryId() == null ? "家庭总支出" : catName.getOrDefault(b.getCategoryId(), "未知分类")));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BudgetVO create(BudgetDTO dto) {
        scope.requireAdmin();
        YearMonth ym = resolveMonth(dto.getBudgetMonth());
        validateCategory(dto.getCategoryId());
        Budget b = new Budget();
        b.setFamilyId(scope.familyId());
        b.setCategoryId(dto.getCategoryId());
        b.setBudgetMonth(ym.toString());
        b.setAmount(dto.getAmount());
        try {
            budgetMapper.insert(b);
        } catch (DuplicateKeyException e) {
            throw new BizException(400, dto.getCategoryId() == null
                    ? "该月家庭总预算已存在，请直接修改"
                    : "该分类该月预算已存在，请直接修改");
        }
        return toVO(b, dto.getCategoryId() == null ? "家庭总支出" : categoryName(dto.getCategoryId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BudgetVO update(Long id, BudgetDTO dto) {
        scope.requireAdmin();
        Budget b = requireBudget(id);
        YearMonth ym = resolveMonth(dto.getBudgetMonth());
        validateCategory(dto.getCategoryId());
        b.setCategoryId(dto.getCategoryId());
        b.setBudgetMonth(ym.toString());
        b.setAmount(dto.getAmount());
        try {
            budgetMapper.updateById(b);
        } catch (DuplicateKeyException e) {
            throw new BizException(400, "该分类该月预算已存在");
        }
        return toVO(b, dto.getCategoryId() == null ? "家庭总支出" : categoryName(dto.getCategoryId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        scope.requireAdmin();
        requireBudget(id);
        budgetMapper.deleteById(id);
    }

    @Override
    public List<BudgetExecutionVO> execution(String month) {
        YearMonth ym = resolveMonth(month);
        List<Budget> budgets = budgetMapper.selectList(
                new LambdaQueryWrapper<Budget>()
                        .eq(Budget::getFamilyId, scope.familyId())
                        .eq(Budget::getBudgetMonth, ym.toString())
                        .orderByAsc(Budget::getId));
        if (budgets.isEmpty()) {
            return List.of();
        }
        // 当月支出流水（全部，内存按分类口径汇总）
        List<Transaction> txns = transactionMapper.selectList(
                new LambdaQueryWrapper<Transaction>()
                        .eq(Transaction::getFamilyId, scope.familyId())
                        .eq(Transaction::getType, 2)
                        .ge(Transaction::getBizDate, ym.atDay(1))
                        .le(Transaction::getBizDate, ym.atEndOfMonth()));
        BigDecimal monthTotal = txns.stream().map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Category> allCats = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().eq(Category::getFamilyId, scope.familyId()));
        Map<Long, String> catName = categoryNameMap();

        List<BudgetExecutionVO> result = new ArrayList<>();
        for (Budget b : budgets) {
            BigDecimal actual;
            if (b.getCategoryId() == null) {
                actual = monthTotal;
            } else {
                List<Long> subtree = new ArrayList<>();
                collectSubTree(allCats, b.getCategoryId(), subtree);
                actual = txns.stream()
                        .filter(t -> subtree.contains(t.getCategoryId()))
                        .map(Transaction::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
            BudgetExecutionVO vo = new BudgetExecutionVO();
            vo.setBudgetId(b.getId());
            vo.setCategoryId(b.getCategoryId());
            vo.setCategoryName(b.getCategoryId() == null ? "家庭总支出" : catName.getOrDefault(b.getCategoryId(), "未知分类"));
            vo.setBudgetMonth(b.getBudgetMonth());
            vo.setAmount(b.getAmount());
            vo.setActual(actual);
            vo.setRate(actual.multiply(BigDecimal.valueOf(100))
                    .divide(b.getAmount(), 1, RoundingMode.HALF_UP));
            vo.setOverrun(actual.compareTo(b.getAmount()) > 0);
            result.add(vo);
        }
        return result;
    }

    // ================= 辅助 =================

    private BudgetVO toVO(Budget b, String categoryName) {
        BudgetVO vo = new BudgetVO();
        vo.setId(b.getId());
        vo.setCategoryId(b.getCategoryId());
        vo.setCategoryName(categoryName);
        vo.setBudgetMonth(b.getBudgetMonth());
        vo.setAmount(b.getAmount());
        return vo;
    }

    /** 预算分类须为当前家庭的支出分类（启用状态） */
    private void validateCategory(Long categoryId) {
        if (categoryId == null) {
            return;
        }
        Category c = categoryMapper.selectById(categoryId);
        if (c == null || !c.getFamilyId().equals(scope.familyId())) {
            throw new BizException("分类不存在");
        }
        if (c.getType() != 2) {
            throw new BizException(400, "预算仅支持支出分类");
        }
        if (c.getStatus() != 1) {
            throw new BizException(400, "分类已停用，不能设置预算");
        }
    }

    private Budget requireBudget(Long id) {
        Budget b = budgetMapper.selectById(id);
        if (b == null || !b.getFamilyId().equals(scope.familyId())) {
            throw new BizException("预算不存在");
        }
        return b;
    }

    private YearMonth resolveMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new BizException(400, "month 格式须为 yyyy-MM，如 2026-09");
        }
    }

    private Map<Long, String> categoryNameMap() {
        Map<Long, String> map = new HashMap<>();
        for (Category c : categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().eq(Category::getFamilyId, scope.familyId()))) {
            map.put(c.getId(), c.getName());
        }
        return map;
    }

    private String categoryName(Long id) {
        Category c = categoryMapper.selectById(id);
        return c == null ? "未知分类" : c.getName();
    }

    private void collectSubTree(List<Category> all, Long rootId, List<Long> out) {
        out.add(rootId);
        for (Category c : all) {
            if (Objects.equals(c.getParentId(), rootId)) {
                collectSubTree(all, c.getId(), out);
            }
        }
    }
}
