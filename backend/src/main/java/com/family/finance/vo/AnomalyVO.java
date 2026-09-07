package com.family.finance.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 异常/关注项（设计文档 6.5 AnomalyVO：类型/维度/本期值/基准/超幅/建议）
 */
@Data
public class AnomalyVO {

    /** 规则编号: R1 超月均 / R2 分类波动 / R3 预算超支 / R4 连续增长 / R5 收入集中 / R6 餐饮专项 / R7 人情往来 */
    private String ruleCode;

    /** 预警标题 */
    private String title;

    /** 级别: danger 严重 / warning 提示 / info 参考卡片 */
    private String level;

    /** 维度描述（如：总支出 / 分类：外卖） */
    private String dimension;

    /** 本期值（本月支出/分类金额等） */
    private BigDecimal current;

    /** 对比基准（月均/预算/上期） */
    private BigDecimal baseline;

    /** 超出基准的百分比（如 31 表示 +31%）；无意义时为空 */
    private BigDecimal exceedPct;

    /** 说明文字（解释为什么是问题，含原因推测） */
    private String description;

    /** 建议动作 */
    private String suggestion;

    /** 钻取：相关分类 id（前端跳转流水列表按此过滤），可为空 */
    private Long drillCategoryId;

    /** 钻取：相关标签（如 礼尚往来），可为空 */
    private String drillTag;
}
