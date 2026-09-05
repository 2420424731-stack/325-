package com.family.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.family.finance.common.BizException;
import com.family.finance.dto.LoanDTO;
import com.family.finance.entity.Loan;
import com.family.finance.mapper.LoanMapper;
import com.family.finance.service.FamilyScopeService;
import com.family.finance.service.LoanService;
import com.family.finance.vo.LoanPlanVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 贷款管理实现（设计文档 F10）。
 * 月供测算（设计文档 9.2）：
 * - 等额本息：月供 M = 本金 × r × (1+r)^n / ((1+r)^n − 1)，r = 年利率 / 12
 * - 等额本金：每期本金 = 本金 / n，利息 = 剩余本金 × r，逐月递减
 * 计划明细以高精度（10 位小数）计算，展示值四舍五入 2 位，末期结清避免尾差。
 */
@Service
@RequiredArgsConstructor
public class LoanServiceImpl implements LoanService {

    private static final Set<String> REPAY_TYPES = Set.of("equal_installment", "equal_principal");
    private static final String EQUAL_INSTALLMENT = "equal_installment";

    private final LoanMapper loanMapper;
    private final FamilyScopeService scope;

    @Override
    public List<Loan> list() {
        return loanMapper.selectList(
                new LambdaQueryWrapper<Loan>()
                        .eq(Loan::getFamilyId, scope.familyId())
                        .orderByAsc(Loan::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Loan create(LoanDTO dto) {
        scope.requireAdmin();
        Loan l = new Loan();
        l.setFamilyId(scope.familyId());
        fill(dto, l);
        // 月供与剩余本金由公式测算（设计文档 9.2 / 5.2.9）
        l.setMonthlyPayment(monthlyPayment(l));
        l.setRemainingPrincipal(dto.getRemainingPrincipal() != null
                ? dto.getRemainingPrincipal() : l.getPrincipal());
        loanMapper.insert(l);
        return l;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Loan update(Long id, LoanDTO dto) {
        scope.requireAdmin();
        Loan l = requireLoan(id);
        fill(dto, l);
        l.setMonthlyPayment(monthlyPayment(l));
        l.setRemainingPrincipal(dto.getRemainingPrincipal() != null
                ? dto.getRemainingPrincipal() : l.getPrincipal());
        loanMapper.updateById(l);
        return l;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        scope.requireAdmin();
        requireLoan(id);
        loanMapper.deleteById(id);
    }

    @Override
    public LoanPlanVO plan(Long id) {
        Loan l = requireLoan(id);
        BigDecimal r = l.getAnnualRate()
                .divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);
        int n = l.getTermMonths();
        BigDecimal remaining = l.getPrincipal();
        BigDecimal monthly = monthlyPayment(l);

        LoanPlanVO vo = new LoanPlanVO();
        vo.setLoanId(l.getId());
        vo.setRepaymentType(l.getRepaymentType());
        vo.setTermMonths(n);
        vo.setMonthlyPayment(monthly.setScale(2, RoundingMode.HALF_UP));

        List<LoanPlanVO.PlanItem> items = new ArrayList<>(n);
        BigDecimal totalInterest = BigDecimal.ZERO;
        BigDecimal totalPayment = BigDecimal.ZERO;
        for (int i = 1; i <= n; i++) {
            BigDecimal interest = remaining.multiply(r).setScale(2, RoundingMode.HALF_UP);
            BigDecimal principal;
            BigDecimal payment;
            if (EQUAL_INSTALLMENT.equals(l.getRepaymentType())) {
                payment = monthly.setScale(2, RoundingMode.HALF_UP);
                principal = payment.subtract(interest);
                // 末期本息结清：本金取剩余值，避免四舍五入尾差
                if (i == n || principal.compareTo(remaining) > 0) {
                    principal = remaining.setScale(2, RoundingMode.HALF_UP);
                    payment = principal.add(interest);
                }
            } else {
                // 等额本金：每期本金固定，末期结清
                BigDecimal base = l.getPrincipal()
                        .divide(BigDecimal.valueOf(n), 10, RoundingMode.HALF_UP)
                        .setScale(2, RoundingMode.HALF_UP);
                principal = i == n ? remaining.setScale(2, RoundingMode.HALF_UP) : base;
                payment = principal.add(interest);
            }
            remaining = remaining.subtract(principal).max(BigDecimal.ZERO);
            totalInterest = totalInterest.add(interest);
            totalPayment = totalPayment.add(payment);

            LoanPlanVO.PlanItem item = new LoanPlanVO.PlanItem();
            item.setPeriod(i);
            item.setPayment(payment);
            item.setPrincipal(principal);
            item.setInterest(interest);
            item.setRemainingPrincipal(remaining.setScale(2, RoundingMode.HALF_UP));
            items.add(item);
        }
        vo.setTotalInterest(totalInterest);
        vo.setTotalPayment(totalPayment);
        vo.setItems(items);
        return vo;
    }

    // ================= 辅助 =================

    /** 月供测算：等额本息固定值；等额本金取首月（本金/n + 首月利息） */
    private BigDecimal monthlyPayment(Loan l) {
        BigDecimal r = l.getAnnualRate()
                .divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);
        BigDecimal p = l.getPrincipal();
        int n = l.getTermMonths();
        if (EQUAL_INSTALLMENT.equals(l.getRepaymentType())) {
            if (r.signum() == 0) {
                return p.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
            }
            double factor = Math.pow(1 + r.doubleValue(), n);
            // M = P × r × (1+r)^n / ((1+r)^n − 1)
            return p.multiply(r)
                    .multiply(BigDecimal.valueOf(factor))
                    .divide(BigDecimal.valueOf(factor - 1), 2, RoundingMode.HALF_UP);
        }
        // 等额本金首月：本金/n + 本金 × r
        return p.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP)
                .add(p.multiply(r).setScale(2, RoundingMode.HALF_UP));
    }

    private void fill(LoanDTO dto, Loan l) {
        l.setName(dto.getName());
        l.setPrincipal(dto.getPrincipal());
        l.setAnnualRate(dto.getAnnualRate());
        l.setTermMonths(dto.getTermMonths());
        l.setStartDate(dto.getStartDate());
        String type = dto.getRepaymentType() == null ? EQUAL_INSTALLMENT : dto.getRepaymentType();
        if (!REPAY_TYPES.contains(type)) {
            throw new BizException(400, "还款方式须为 equal_installment(等额本息) 或 equal_principal(等额本金)");
        }
        l.setRepaymentType(type);
        l.setLender(dto.getLender());
    }

    /** 贷款存在且属于当前家庭（越权视为不存在） */
    private Loan requireLoan(Long id) {
        Loan l = loanMapper.selectById(id);
        if (l == null || !l.getFamilyId().equals(scope.familyId())) {
            throw new BizException("贷款不存在");
        }
        return l;
    }
}
