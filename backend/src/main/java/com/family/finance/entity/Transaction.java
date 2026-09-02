package com.family.finance.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 收支记录（核心表）
 */
@Data
@TableName("transaction")
public class Transaction {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属家庭 */
    private Long familyId;

    /** 经手成员 family_member.id（空=家庭整体） */
    private Long memberId;

    /** 类型: 1 收入 / 2 支出 */
    private Integer type;

    /** 归属分类（叶子分类） */
    private Long categoryId;

    /** 金额(>0) */
    private BigDecimal amount;

    /** 业务发生日期 */
    private LocalDate bizDate;

    /** 商家/对方 */
    private String merchant;

    /** 消费片区 */
    private String region;

    /** 标签(逗号分隔), 如: 礼尚往来,生日 */
    private String tags;

    /** 支付方式: 支付宝/微信/银行卡/现金/其他 */
    private String paymentMethod;

    /** 凭证图片地址 */
    private String image;

    /** 备注 */
    private String note;

    /** 录入人 user.id */
    private Long createdBy;

    /** 逻辑删除: 0 正常 1 已删 */
    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
