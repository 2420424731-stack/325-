package com.family.finance.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 贷款还款计划（设计文档 9.2）：逐月 [期次、月供、本金、利息、剩余本金]
 */
@Data
public class LoanPlanVO {

    private Long loanId;
    private String repaymentType;
    /** 还款期数 */
    private Integer termMonths;
    /** 月供（等额本息固定；等额本金为首月，逐月递减） */
    private BigDecimal monthlyPayment;
    /** 利息合计 */
    private BigDecimal totalInterest;
    /** 本息合计 */
    private BigDecimal totalPayment;
    /** 逐月明细 */
    private List<PlanItem> items;

    @Data
    public static class PlanItem {
        /** 期次（从 1 开始） */
        private Integer period;
        /** 当期月供 */
        private BigDecimal payment;
        /** 当期本金 */
        private BigDecimal principal;
        /** 当期利息 */
        private BigDecimal interest;
        /** 期末剩余本金 */
        private BigDecimal remainingPrincipal;
    }
}
