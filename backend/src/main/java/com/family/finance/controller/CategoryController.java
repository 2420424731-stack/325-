package com.family.finance.controller;

import com.family.finance.common.Result;
import com.family.finance.dto.CategoryDTO;
import com.family.finance.service.CategoryService;
import com.family.finance.vo.CategoryNodeVO;
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
 * 收支分类接口（管理需 ADMIN；树查询家庭内共享）
 */
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /** 分类树（type: 1 收入 / 2 支出） */
    @GetMapping("/tree")
    public Result<List<CategoryNodeVO>> tree(@RequestParam Integer type) {
        return Result.ok(categoryService.tree(type));
    }

    /** 新增分类（仅管理员） */
    @PostMapping
    public Result<CategoryNodeVO> add(@Valid @RequestBody CategoryDTO dto) {
        return Result.ok(categoryService.add(dto));
    }

    /** 修改分类名称/图标/排序（仅管理员） */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody CategoryDTO dto) {
        categoryService.update(id, dto);
        return Result.ok();
    }

    /** 删除分类（仅管理员；内置分类/有子分类/有流水禁删） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.ok();
    }
}
