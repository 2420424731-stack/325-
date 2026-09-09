package com.family.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 资产新增/修改请求（v1 不支持修改 assetType，改类型请删除重建）。
 * 校验分组：Create 组约束必填字段；Update 组仅校验已传字段（支持只改估值）。
 */
@Data
public class AssetDTO {

    /** 新增校验组（必填字段） */
    public interface Create {}

    /** 局部更新校验组 */
    public interface Update {}

    /** 类型: 房产/存款/汽车/其他 */
    @NotBlank(message = "资产类型不能为空", groups = Create.class)
    @Size(max = 20, message = "资产类型最长 20 字符", groups = {Create.class, Update.class})
    private String assetType;

    @NotBlank(message = "资产名称不能为空", groups = Create.class)
    @Size(max = 100, message = "资产名称最长 100 字符", groups = {Create.class, Update.class})
    private String name;

    /** 当前估值 */
    @NotNull(message = "资产估值不能为空", groups = Create.class)
    @DecimalMin(value = "0", message = "估值不能为负", groups = {Create.class, Update.class})
    private BigDecimal value;

    /** 购置日期 */
    private LocalDate purchaseDate;

    @Size(max = 255, message = "备注最长 255 字符", groups = {Create.class, Update.class})
    private String note;
}
