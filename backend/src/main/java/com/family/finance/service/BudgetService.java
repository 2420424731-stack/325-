package com.family.finance.service;

import com.family.finance.dto.BudgetDTO;
import com.family.finance.vo.BudgetExecutionVO;
import com.family.finance.vo.BudgetVO;

import java.util.List;

/**
 * 预算管理（设计文档 F11）：按分类（或家庭整体）设置月度预算并跟踪执行率
 */
public interface BudgetService {

    /** 某月预算列表（month: yyyy-MM，含分类名） */
    List<BudgetVO> list(String month);

    BudgetVO create(BudgetDTO dto);

    BudgetVO update(Long id, BudgetDTO dto);

    void delete(Long id);

    /** 某月预算执行情况（分类预算按含子孙分类口径汇总） */
    List<BudgetExecutionVO> execution(String month);
}
