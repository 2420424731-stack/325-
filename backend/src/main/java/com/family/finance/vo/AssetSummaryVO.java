package com.family.finance.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 资产汇总（设计文档 8.1）：总资产、总贷款、净资产（总资产 − 总贷款）、按类型分布
 */
@Data
public class AssetSummaryVO {

    /** 总资产（资产估值合计） */
    private BigDecimal totalAssets;
    /** 总贷款（剩余本金合计，未填剩余本金按本金计） */
    private BigDecimal totalLoans;
    /** 净资产 = 总资产 − 总贷款 */
    private BigDecimal netAssets;
    /** 资产按类型分布（name=类型名，total=金额，count=笔数） */
    private List<NameStatVO> byType;
}
