package com.family.finance.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 收支分页查询条件（GET /api/transactions 的 query 参数）
 */
@Data
public class TransactionQuery {

    /** 页码，默认 1 */
    private Long page = 1L;

    /** 每页条数，默认 10，最大 100 */
    private Long size = 10L;

    /** 类型: 1 收入 / 2 支出（空=全部） */
    private Integer type;

    /** 分类 id（父分类会带上其所有子孙分类） */
    private Long categoryId;

    /** 经手成员 */
    private Long memberId;

    /** 商家（模糊匹配） */
    private String merchant;

    /** 片区（模糊匹配） */
    private String region;

    /** 关键词：匹配 商家/备注/标签（模糊） */
    private String keyword;

    /** 业务日期起 */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    /** 业务日期止 */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;
}
