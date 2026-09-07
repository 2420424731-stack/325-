package com.family.finance.controller;

import com.family.finance.common.PageResult;
import com.family.finance.common.Result;
import com.family.finance.dto.TransactionDTO;
import com.family.finance.dto.TransactionQuery;
import com.family.finance.service.TransactionService;
import com.family.finance.vo.TransactionVO;
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

/**
 * 收支记录接口（家庭内共享可见；改/删限本人或管理员）
 */
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    /** 分页 + 多条件查询 */
    @GetMapping
    public Result<PageResult<TransactionVO>> page(TransactionQuery query) {
        return Result.ok(transactionService.page(query));
    }

    /** 详情 */
    @GetMapping("/{id}")
    public Result<TransactionVO> get(@PathVariable Long id) {
        return Result.ok(transactionService.get(id));
    }

    /** 新增 */
    @PostMapping
    public Result<TransactionVO> create(@Valid @RequestBody TransactionDTO dto) {
        return Result.ok(transactionService.create(dto));
    }

    /** 修改（局部更新） */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody TransactionDTO dto) {
        transactionService.update(id, dto);
        return Result.ok();
    }

    /** 删除（逻辑删除） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        transactionService.delete(id);
        return Result.ok();
    }
}
