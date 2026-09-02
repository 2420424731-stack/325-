package com.family.finance.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 股票/基金持仓（拓展）
 */
@Data
@TableName("asset_position")
public class AssetPosition {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属资产 asset.id（asset_type=股票基金） */
    private Long assetId;

    /** 证券代码, 如 600519 */
    private String code;

    /** 证券名称 */
    private String name;

    /** 持仓数量 */
    private Integer shares;

    /** 成本价 */
    private BigDecimal costPrice;

    /** 现价（手工更新） */
    private BigDecimal currentPrice;

    /** 估值更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
