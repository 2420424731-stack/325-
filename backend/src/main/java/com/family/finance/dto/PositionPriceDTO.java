package com.family.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 持仓现价更新请求（PUT /api/assets/positions/{id}/price）
 */
@Data
public class PositionPriceDTO {

    @NotNull(message = "现价不能为空")
    @DecimalMin(value = "0", message = "现价不能为负")
    @Digits(integer = 8, fraction = 4, message = "现价最多 4 位小数")
    private BigDecimal currentPrice;
}
