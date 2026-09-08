package com.family.finance.vo;

import lombok.Data;

/**
 * 收支导出视图：CSV 文本已带 UTF-8 BOM（﻿），
 * Excel 直接打开不乱码；前端按 text/csv 生成下载文件
 */
@Data
public class TransactionExportVO {

    /** 导出条数 */
    private Long count;

    /** CSV 内容（含表头，\r\n 换行，字段已做转义与公式注入防护） */
    private String csv;
}
