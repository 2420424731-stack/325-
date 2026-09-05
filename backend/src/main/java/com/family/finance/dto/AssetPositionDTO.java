package com.family.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 股票/基金持仓新增/修改请求（仅股票基金类资产可挂持仓）
 */
@Data
public class AssetPositionDTO {

    /** 证券代码, 如 600519 */
    @Size(max = 20, message = "证券代码最长 20 字符")
    private String code;

    /** 证券名称 */
    @Size(max = 50, message = "证券名称最长 50 字符")
    private String name;

    /** 持仓数量 */
    @NotNull(message = "持仓数量不能为空")
    @Min(value = 1, message = "持仓数量至少为 1")
    private Integer shares;

    /** 成本价 */
    @DecimalMin(value = "0", message = "成本价不能为负")
    @Digits(integer = 8, fraction = 4, message = "成本价最多 4 位小数")
    private BigDecimal costPrice;

    /** 现价（手工更新） */
    @NotNull(message = "现价不能为空")
    @DecimalMin(value = "0", message = "现价不能为负")
    @Digits(integer = 8, fraction = 4, message = "现价最多 4 位小数")
    private BigDecimal currentPrice;
}
