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
 * 预算（拓展）
 */
@Data
@TableName("budget")
public class Budget {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属家庭 */
    private Long familyId;

    /** 预算分类 category.id（空=家庭总预算） */
    private Long categoryId;

    /** 预算月份, 如 2026-09 */
    private String budgetMonth;

    /** 预算金额 */
    private BigDecimal amount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
