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
 * 贷款（拓展：房贷/车贷）
 */
@Data
@TableName("loan")
public class Loan {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属家庭 */
    private Long familyId;

    /** 贷款名称: 房贷/车贷/消费贷 */
    private String name;

    /** 贷款本金 */
    private BigDecimal principal;

    /** 年利率, 如 0.0380 */
    private BigDecimal annualRate;

    /** 期数(月) */
    private Integer termMonths;

    /** 起贷日期 */
    private LocalDate startDate;

    /** 还款方式: equal_installment 等额本息 / equal_principal 等额本金 */
    private String repaymentType;

    /** 月供（等额本息固定/等额本金为首月） */
    private BigDecimal monthlyPayment;

    /** 剩余本金（按月更新） */
    private BigDecimal remainingPrincipal;

    /** 贷款机构 */
    private String lender;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
