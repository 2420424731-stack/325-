package com.family.finance.service;

import com.family.finance.dto.CategoryDTO;
import com.family.finance.vo.CategoryNodeVO;

import java.util.List;

/**
 * 收支分类管理（多级树形、可自定义）
 */
public interface CategoryService {

    /** 分类树（type: 1 收入 / 2 支出；含停用，由前端置灰） */
    List<CategoryNodeVO> tree(Integer type);

    /** 新增分类（最多三级；父分类须同家庭同类型且启用） */
    CategoryNodeVO add(CategoryDTO dto);

    /** 修改分类名称/图标/排序/启停用（type 仅校验一致） */
    void update(Long id, CategoryDTO dto);

    /** 删除分类（内置分类、有子分类、有流水记录者禁删） */
    void delete(Long id);
}
