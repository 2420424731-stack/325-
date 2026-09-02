package com.family.finance.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 通用名称维度汇总项（成员/商家/片区/标签共用）
 */
@Data
public class NameStatVO {

    private String name;
    private BigDecimal total;
    private Long count;
}
