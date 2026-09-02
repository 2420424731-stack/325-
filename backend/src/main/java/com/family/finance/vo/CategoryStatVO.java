package com.family.finance.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 分类汇总项（饼图/柱状图数据源）
 */
@Data
public class CategoryStatVO {

    private Long categoryId;
    private String categoryName;
    /** 类型: 1 收入 / 2 支出 */
    private Integer type;
    private BigDecimal total;
    private Long count;
}
