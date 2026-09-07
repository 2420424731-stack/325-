package com.family.finance.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 资产视图
 */
@Data
public class AssetVO {

    private Long id;
    private String assetType;
    private String name;
    private BigDecimal value;
    private LocalDate purchaseDate;
    private String note;
}
