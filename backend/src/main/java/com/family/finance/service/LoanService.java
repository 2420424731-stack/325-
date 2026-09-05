package com.family.finance.service;

import com.family.finance.dto.LoanDTO;
import com.family.finance.entity.Loan;
import com.family.finance.vo.LoanPlanVO;

import java.util.List;

/**
 * 贷款管理（设计文档 F10）：房贷/车贷登记与还款计划测算
 */
public interface LoanService {

    List<Loan> list();

    Loan create(LoanDTO dto);

    Loan update(Long id, LoanDTO dto);

    void delete(Long id);

    /** 还款计划测算（等额本息/等额本金，设计文档 9.2） */
    LoanPlanVO plan(Long id);
}
