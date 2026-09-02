package com.family.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.family.finance.common.BizException;
import com.family.finance.common.PageResult;
import com.family.finance.dto.TransactionDTO;
import com.family.finance.dto.TransactionQuery;
import com.family.finance.entity.Category;
import com.family.finance.entity.FamilyMember;
import com.family.finance.entity.Transaction;
import com.family.finance.entity.User;
import com.family.finance.mapper.CategoryMapper;
import com.family.finance.mapper.FamilyMemberMapper;
import com.family.finance.mapper.TransactionMapper;
import com.family.finance.mapper.UserMapper;
import com.family.finance.service.FamilyScopeService;
import com.family.finance.service.TransactionService;
import com.family.finance.vo.TransactionVO;
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
 * 收支记录服务。
 * 数据隔离：所有查询强制 family_id；越权操作视为「记录不存在」。
 * 权限：普通成员可记账，仅能改/删本人创建的记录；管理员可管理全部（设计文档 6.4）。
 */
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final TransactionMapper transactionMapper;
    private final CategoryMapper categoryMapper;
    private final FamilyMemberMapper memberMapper;
    private final UserMapper userMapper;
    private final FamilyScopeService scope;

    @Override
    public PageResult<TransactionVO> page(TransactionQuery q) {
        Long familyId = scope.familyId();
        Long pageNo = q.getPage() == null || q.getPage() < 1 ? 1L : q.getPage();
        Long size = q.getSize() == null || q.getSize() < 1 ? 10L : Math.min(q.getSize(), 100L);

        LambdaQueryWrapper<Transaction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Transaction::getFamilyId, familyId);
        if (q.getType() != null) {
            wrapper.eq(Transaction::getType, q.getType());
        }
        // 选了父分类时带上其所有子孙分类（对应设计文档 5.4 SQL 思路：category_id IN 子树）
        if (q.getCategoryId() != null) {
            List<Long> ids = new ArrayList<>();
            collectChildIds(familyId, q.getCategoryId(), ids);
            wrapper.in(Transaction::getCategoryId, ids);
        }
        if (q.getMemberId() != null) {
            wrapper.eq(Transaction::getMemberId, q.getMemberId());
        }
        if (StringUtils.hasText(q.getMerchant())) {
            wrapper.like(Transaction::getMerchant, q.getMerchant().trim());
        }
        if (StringUtils.hasText(q.getRegion())) {
            wrapper.like(Transaction::getRegion, q.getRegion().trim());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            wrapper.and(w -> w.like(Transaction::getMerchant, kw)
                    .or().like(Transaction::getNote, kw)
                    .or().like(Transaction::getTags, kw));
        }
        if (q.getStartDate() != null) {
            wrapper.ge(Transaction::getBizDate, q.getStartDate());
        }
        if (q.getEndDate() != null) {
            wrapper.le(Transaction::getBizDate, q.getEndDate());
        }
        wrapper.orderByDesc(Transaction::getBizDate).orderByDesc(Transaction::getId);

        Page<Transaction> p = transactionMapper.selectPage(new Page<>(pageNo, size), wrapper);
        return PageResult.of(buildVOList(p.getRecords()), p.getTotal(), p.getCurrent(), p.getSize());
    }

    @Override
    public TransactionVO get(Long id) {
        return buildVOList(List.of(requireTransaction(id))).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransactionVO create(TransactionDTO dto) {
        Long familyId = scope.familyId();
        validateCategory(familyId, dto.getCategoryId(), dto.getType());
        validateMember(familyId, dto.getMemberId());

        Transaction t = new Transaction();
        applyDto(t, dto);
        t.setFamilyId(familyId);
        t.setCreatedBy(scope.userId());
        transactionMapper.insert(t);
        return buildVOList(List.of(t)).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, TransactionDTO dto) {
        Transaction t = requireTransaction(id);
        checkOperable(t);

        // 局部更新：DTO 中为 null 的字段保留原值；分类/成员若有变更需重新校验
        if (dto.getCategoryId() != null && !Objects.equals(t.getCategoryId(), dto.getCategoryId())) {
            validateCategory(scope.familyId(), dto.getCategoryId(), dto.getType());
        }
        if (dto.getMemberId() != null && !Objects.equals(t.getMemberId(), dto.getMemberId())) {
            validateMember(scope.familyId(), dto.getMemberId());
        }
        applyDto(t, dto);
        transactionMapper.updateById(t);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Transaction t = requireTransaction(id);
        checkOperable(t);
        transactionMapper.deleteById(id); // @TableLogic → deleted=1
    }

    // ---------- 私有辅助 ----------

    /** 按 id+familyId 取记录，越权/不存在统一视为不存在 */
    private Transaction requireTransaction(Long id) {
        Transaction t = transactionMapper.selectOne(
                new LambdaQueryWrapper<Transaction>()
                        .eq(Transaction::getId, id)
                        .eq(Transaction::getFamilyId, scope.familyId()));
        if (t == null) {
            throw new BizException("记录不存在");
        }
        return t;
    }

    /** 修改/删除权限：本人创建 或 管理员 */
    private void checkOperable(Transaction t) {
        if (!Objects.equals(t.getCreatedBy(), scope.userId()) && !scope.isAdmin()) {
            throw new BizException(403, "只能操作本人录入的记录");
        }
    }

    /** 校验分类：同家庭、type 一致、叶子分类（无子类）、启用状态 */
    private void validateCategory(Long familyId, Long categoryId, Integer type) {
        if (type == null || (type != 1 && type != 2)) {
            throw new BizException(400, "type 必须为 1(收入)或 2(支出)");
        }
        Category c = categoryMapper.selectOne(
                new LambdaQueryWrapper<Category>()
                        .eq(Category::getId, categoryId)
                        .eq(Category::getFamilyId, familyId));
        if (c == null) {
            throw new BizException(400, "所选分类不存在");
        }
        if (!Objects.equals(c.getType(), type)) {
            throw new BizException(400, "分类与收支类型不匹配");
        }
        Long childCount = categoryMapper.selectCount(
                new LambdaQueryWrapper<Category>().eq(Category::getParentId, categoryId));
        if (childCount > 0) {
            throw new BizException(400, "请选择最末级分类记账");
        }
        if (c.getStatus() != 1) {
            throw new BizException(400, "所选分类已停用，请先启用");
        }
    }

    /** 校验成员（可空）：同家庭且启用 */
    private void validateMember(Long familyId, Long memberId) {
        if (memberId == null) {
            return;
        }
        FamilyMember m = memberMapper.selectOne(
                new LambdaQueryWrapper<FamilyMember>()
                        .eq(FamilyMember::getId, memberId)
                        .eq(FamilyMember::getFamilyId, familyId));
        if (m == null) {
            throw new BizException(400, "所选成员不存在");
        }
        if (m.getStatus() != 1) {
            throw new BizException(400, "所选成员已停用");
        }
    }

    /** DTO → 实体（null 不覆盖，空串归一为 null 便于前端回显） */
    private void applyDto(Transaction t, TransactionDTO dto) {
        if (dto.getType() != null) {
            t.setType(dto.getType());
        }
        if (dto.getCategoryId() != null) {
            t.setCategoryId(dto.getCategoryId());
        }
        if (dto.getAmount() != null) {
            t.setAmount(dto.getAmount());
        }
        if (dto.getBizDate() != null) {
            t.setBizDate(dto.getBizDate());
        }
        if (dto.getMemberId() != null) {
            t.setMemberId(dto.getMemberId());
        }
        t.setMerchant(trimToNull(dto.getMerchant()));
        t.setRegion(trimToNull(dto.getRegion()));
        t.setTags(trimToNull(dto.getTags()));
        t.setPaymentMethod(trimToNull(dto.getPaymentMethod()));
        t.setImage(trimToNull(dto.getImage()));
        t.setNote(trimToNull(dto.getNote()));
    }

    /** 收集分类 id 及其全部子孙 id（历史流水可能挂在停用子分类上，故含全部状态） */
    private void collectChildIds(Long familyId, Long parentId, List<Long> out) {
        List<Category> all = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().eq(Category::getFamilyId, familyId));
        collectChildIds(parentId, all, out);
    }

    private void collectChildIds(Long parentId, List<Category> all, List<Long> out) {
        for (Category c : all) {
            if (Objects.equals(c.getParentId(), parentId)) {
                out.add(c.getId());
                collectChildIds(c.getId(), all, out);
            }
        }
        if (out.isEmpty()) {
            out.add(parentId); // 叶子分类，仅自身
        }
    }

    /** 批量组装 VO：分类名/成员名/录入人昵称 */
    private List<TransactionVO> buildVOList(List<Transaction> records) {
        Long familyId = scope.familyId();
        Map<Long, Category> categoryMap = new HashMap<>();
        for (Category c : categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().eq(Category::getFamilyId, familyId))) {
            categoryMap.put(c.getId(), c);
        }
        Map<Long, String> memberNameMap = new HashMap<>();
        for (FamilyMember m : memberMapper.selectList(
                new LambdaQueryWrapper<FamilyMember>().eq(FamilyMember::getFamilyId, familyId))) {
            memberNameMap.put(m.getId(), m.getName());
        }
        Map<Long, String> userNameMap = new HashMap<>();
        for (User u : userMapper.selectBatchIds(
                records.stream().map(Transaction::getCreatedBy).filter(Objects::nonNull).distinct().toList())) {
            userNameMap.put(u.getId(), u.getNickname() == null ? u.getUsername() : u.getNickname());
        }
        List<TransactionVO> vos = new ArrayList<>();
        for (Transaction t : records) {
            TransactionVO vo = buildVO(t);
            Category c = categoryMap.get(t.getCategoryId());
            if (c != null) {
                vo.setCategoryName(c.getName());
            }
            vo.setMemberName(memberNameMap.get(t.getMemberId()));
            vo.setCreatedByName(userNameMap.get(t.getCreatedBy()));
            vos.add(vo);
        }
        return vos;
    }

    private TransactionVO buildVO(Transaction t) {
        TransactionVO vo = new TransactionVO();
        vo.setId(t.getId());
        vo.setMemberId(t.getMemberId());
        vo.setType(t.getType());
        vo.setCategoryId(t.getCategoryId());
        vo.setAmount(t.getAmount());
        vo.setBizDate(t.getBizDate());
        vo.setMerchant(t.getMerchant());
        vo.setRegion(t.getRegion());
        vo.setTags(t.getTags());
        vo.setPaymentMethod(t.getPaymentMethod());
        vo.setImage(t.getImage());
        vo.setNote(t.getNote());
        vo.setCreatedAt(t.getCreatedAt());
        vo.setUpdatedAt(t.getUpdatedAt());
        return vo;
    }

    private String trimToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
