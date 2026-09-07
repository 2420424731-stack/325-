package com.family.finance.controller;

import com.family.finance.common.Result;
import com.family.finance.dto.BudgetDTO;
import com.family.finance.service.BudgetService;
import com.family.finance.vo.BudgetExecutionVO;
import com.family.finance.vo.BudgetVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 预算管理接口（设计文档 F11：月度预算与执行率，管理需 ADMIN）
 */
@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    /** 某月预算列表（month 必填，yyyy-MM） */
    @GetMapping
    public Result<List<BudgetVO>> list(@RequestParam String month) {
        return Result.ok(budgetService.list(month));
    }

    /** 某月预算执行率（分类预算按含子孙分类口径） */
    @GetMapping("/execution")
    public Result<List<BudgetExecutionVO>> execution(@RequestParam String month) {
        return Result.ok(budgetService.execution(month));
    }

    /** 新增预算（仅管理员；同分类同月唯一） */
    @PostMapping
    public Result<BudgetVO> create(@Valid @RequestBody BudgetDTO dto) {
        return Result.ok(budgetService.create(dto));
    }

    /** 修改预算（仅管理员） */
    @PutMapping("/{id}")
    public Result<BudgetVO> update(@PathVariable Long id, @Valid @RequestBody BudgetDTO dto) {
        return Result.ok(budgetService.update(id, dto));
    }

    /** 删除预算（仅管理员） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        budgetService.delete(id);
        return Result.ok();
    }
}
