package com.family.finance.controller;

import com.family.finance.common.Result;
import com.family.finance.dto.LoanDTO;
import com.family.finance.entity.Loan;
import com.family.finance.service.LoanService;
import com.family.finance.vo.LoanPlanVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 贷款管理接口（设计文档 F10：房贷/车贷与还款计划测算，管理需 ADMIN）
 */
@RestController
@RequestMapping("/api/loans")
@RequiredArgsConstructor
public class LoanController {

    private final LoanService loanService;

    /** 贷款列表 */
    @GetMapping
    public Result<List<Loan>> list() {
        return Result.ok(loanService.list());
    }

    /** 新增贷款（仅管理员；月供自动测算） */
    @PostMapping
    public Result<Loan> create(@Valid @RequestBody LoanDTO dto) {
        return Result.ok(loanService.create(dto));
    }

    /** 修改贷款（仅管理员） */
    @PutMapping("/{id}")
    public Result<Loan> update(@PathVariable Long id, @Valid @RequestBody LoanDTO dto) {
        return Result.ok(loanService.update(id, dto));
    }

    /** 删除贷款（仅管理员） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        loanService.delete(id);
        return Result.ok();
    }

    /** 还款计划测算（等额本息/等额本金逐月明细） */
    @GetMapping("/{id}/plan")
    public Result<LoanPlanVO> plan(@PathVariable Long id) {
        return Result.ok(loanService.plan(id));
    }
}
