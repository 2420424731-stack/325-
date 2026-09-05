package com.family.finance.vo;

import com.family.finance.entity.AssetPosition;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 资产视图：实体 + 持仓明细（股票基金类）。
 * marketValue 为持仓市值合计（shares × currentPrice），其余类型为 null。
 */
@Data
public class AssetVO {

    private Long id;
    private String assetType;
    private String name;
    private BigDecimal value;
    private LocalDate purchaseDate;
    private String note;
    /** 持仓明细（仅股票基金类） */
    private List<AssetPosition> positions;
    /** 持仓市值合计（仅股票基金类；现价缺失按成本价计） */
    private BigDecimal marketValue;
}
