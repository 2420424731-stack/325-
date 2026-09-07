package com.family.finance.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 收支记录视图：在实体基础上补充 分类名/成员名/录入人 便于列表直接展示
 */
@Data
public class TransactionVO {

    private Long id;
    private Long memberId;
    private Integer type;
    private Long categoryId;
    private BigDecimal amount;
    private LocalDate bizDate;
    private String merchant;
    private String region;
    private String tags;
    private String paymentMethod;
    private String image;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 分类名（叶子分类，含父级路径时由前端拼接） */
    private String categoryName;
    /** 成员姓名 */
    private String memberName;
    /** 录入人昵称 */
    private String createdByName;
}
