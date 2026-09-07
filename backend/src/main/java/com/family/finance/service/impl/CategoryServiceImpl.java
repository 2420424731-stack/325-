package com.family.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.family.finance.common.BizException;
import com.family.finance.dto.CategoryDTO;
import com.family.finance.entity.Category;
import com.family.finance.entity.Transaction;
import com.family.finance.mapper.CategoryMapper;
import com.family.finance.mapper.TransactionMapper;
import com.family.finance.service.CategoryService;
import com.family.finance.service.FamilyScopeService;
import com.family.finance.vo.CategoryNodeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 收支分类管理：家庭内共享、按收支类型隔离、最多三级树。
 * 课程要求「分类可自定义」：注册时注入内置模板，此处支持用户增删改，
 * 约束：系统内置顶层不可删、有子分类/有流水不可删（可停用代替）。
 */
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;
    private final TransactionMapper transactionMapper;
    private final FamilyScopeService scope;

    @Override
    public List<CategoryNodeVO> tree(Integer type) {
        if (type == null || (type != 1 && type != 2)) {
            throw new BizException(400, "type 必须为 1(收入)或 2(支出)");
        }
        List<Category> all = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>()
                        .eq(Category::getFamilyId, scope.familyId())
                        .eq(Category::getType, type)
                        .orderByAsc(Category::getSortOrder)
                        .orderByAsc(Category::getId));

        // 内存建树：先建全部节点，再把非顶级挂到父节点下（同家庭数据完整，理论上无孤儿）
        List<CategoryNodeVO> roots = new ArrayList<>();
        Map<Long, CategoryNodeVO> nodeMap = new HashMap<>();
        for (Category c : all) {
            CategoryNodeVO node = new CategoryNodeVO();
            node.setId(c.getId());
            node.setParentId(c.getParentId());
            node.setType(c.getType());
            node.setName(c.getName());
            node.setIcon(c.getIcon());
            node.setSortOrder(c.getSortOrder());
            node.setIsSystem(c.getIsSystem());
            node.setStatus(c.getStatus());
            nodeMap.put(c.getId(), node);
        }
        for (CategoryNodeVO node : nodeMap.values()) {
            CategoryNodeVO parent = nodeMap.get(node.getParentId());
            if (node.getParentId() == 0L || parent == null) {
                roots.add(node); // 孤儿兜底也当顶级展示，避免数据丢失
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CategoryNodeVO add(CategoryDTO dto) {
        scope.requireAdmin();
        validateType(dto.getType());
        Long familyId = scope.familyId();
        Long parentId = dto.getParentId() == null ? 0L : dto.getParentId();

        if (parentId != 0L) {
            Category parent = requireCategory(parentId);
            // 父分类必须同类型，且为启用状态（停用分类下不再挂新子类）
            if (!Objects.equals(parent.getType(), dto.getType())) {
                throw new BizException(400, "父分类与子分类类型必须一致");
            }
            if (parent.getStatus() != 1) {
                throw new BizException(400, "父分类已停用，请先启用后再添加子分类");
            }
            // 层级限制：父分类为二级（其 parent 为顶级）时才允许挂三级；否则最多到三级
            if (parent.getParentId() != 0L) {
                Category grand = categoryMapper.selectById(parent.getParentId());
                if (grand == null || grand.getParentId() != 0L) {
                    throw new BizException(400, "分类最多支持三级");
                }
            }
        }

        Category category = new Category();
        category.setFamilyId(familyId);
        category.setParentId(parentId);
        category.setType(dto.getType());
        category.setName(dto.getName().trim());
        category.setIcon(StringUtils.hasText(dto.getIcon()) ? dto.getIcon().trim() : null);
        category.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        category.setIsSystem(0);
        category.setStatus(1);
        categoryMapper.insert(category);

        CategoryNodeVO node = new CategoryNodeVO();
        node.setId(category.getId());
        node.setParentId(category.getParentId());
        node.setType(category.getType());
        node.setName(category.getName());
        node.setIcon(category.getIcon());
        node.setSortOrder(category.getSortOrder());
        node.setIsSystem(0);
        node.setStatus(1);
        return node;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, CategoryDTO dto) {
        scope.requireAdmin();
        Category category = requireCategory(id);
        validateType(dto.getType());
        // 类型为不可变属性：新值与旧值不一致直接拒绝（dto 中传 type 仅用于一致性校验）
        if (!Objects.equals(category.getType(), dto.getType())) {
            throw new BizException(400, "不能修改分类类型");
        }
        category.setName(dto.getName().trim());
        category.setIcon(StringUtils.hasText(dto.getIcon()) ? dto.getIcon().trim() : null);
        if (dto.getSortOrder() != null) {
            category.setSortOrder(dto.getSortOrder());
        }
        if (dto.getStatus() != null) {
            if (dto.getStatus() != 0 && dto.getStatus() != 1) {
                throw new BizException(400, "status 必须为 0(停用)或 1(启用)");
            }
            category.setStatus(dto.getStatus());
        }
        categoryMapper.updateById(category);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        scope.requireAdmin();
        Category category = requireCategory(id);
        if (category.getIsSystem() == 1) {
            throw new BizException(400, "系统内置分类不可删除，可停用（编辑状态）");
        }
        Long childCount = categoryMapper.selectCount(
                new LambdaQueryWrapper<Category>().eq(Category::getParentId, id));
        if (childCount > 0) {
            throw new BizException(400, "该分类下存在子分类，请先删除子分类");
        }
        Long used = transactionMapper.selectCount(
                new LambdaQueryWrapper<Transaction>()
                        .eq(Transaction::getFamilyId, scope.familyId())
                        .eq(Transaction::getCategoryId, id));
        if (used > 0) {
            throw new BizException(400, "该分类下已有收支记录，不能删除，可停用");
        }
        categoryMapper.deleteById(id);
    }

    /** 校验分类属于当前家庭，否则视为不存在（防越权） */
    private Category requireCategory(Long id) {
        Category category = categoryMapper.selectOne(
                new LambdaQueryWrapper<Category>()
                        .eq(Category::getId, id)
                        .eq(Category::getFamilyId, scope.familyId()));
        if (category == null) {
            throw new BizException("分类不存在");
        }
        return category;
    }

    private void validateType(Integer type) {
        if (type == null || (type != 1 && type != 2)) {
            throw new BizException(400, "type 必须为 1(收入)或 2(支出)");
        }
    }
}
