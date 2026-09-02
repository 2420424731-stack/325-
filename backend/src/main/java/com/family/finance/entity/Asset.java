package com.family.finance.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 资产（拓展）
 */
@Data
@TableName("asset")
public class Asset {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属家庭 */
    private Long familyId;

    /** 类型: 房产/股票基金/存款/汽车/其他 */
    private String assetType;

    /** 资产名称 */
    private String name;

    /** 当前估值 */
    private BigDecimal value;

    /** 购置日期 */
    private LocalDate purchaseDate;

    /** 备注 */
    private String note;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
