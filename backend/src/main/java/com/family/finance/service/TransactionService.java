package com.family.finance.service;

import com.family.finance.common.PageResult;
import com.family.finance.dto.TransactionDTO;
import com.family.finance.dto.TransactionQuery;
import com.family.finance.vo.TransactionExportVO;
import com.family.finance.vo.TransactionVO;

/**
 * 收支记录：分页查询 / 详情 / 新增 / 修改 / 删除（逻辑删除）/ CSV 导出
 */
public interface TransactionService {

    /** 分页 + 多条件查询 */
    PageResult<TransactionVO> page(TransactionQuery query);

    /** 按筛选条件全量导出 CSV（日期升序；超过 5 万条拒绝，提示缩小范围） */
    TransactionExportVO export(TransactionQuery query);

    /** 详情（编辑回显） */
    TransactionVO get(Long id);

    /** 新增（管理员与普通成员均可记账） */
    TransactionVO create(TransactionDTO dto);

    /** 修改（仅本人或管理员） */
    void update(Long id, TransactionDTO dto);

    /** 删除（逻辑删除，仅本人或管理员） */
    void delete(Long id);
}
