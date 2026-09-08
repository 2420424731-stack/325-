package com.family.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import com.family.finance.vo.TransactionExportVO;
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

    /** 导出上限：家庭记账个人量级远达不到，防止异常全量导出拖垮服务 */
    private static final int EXPORT_LIMIT = 50_000;

    @Override
    public PageResult<TransactionVO> page(TransactionQuery q) {
        Long familyId = scope.familyId();
        Long pageNo = q.getPage() == null || q.getPage() < 1 ? 1L : q.getPage();
        Long size = q.getSize() == null || q.getSize() < 1 ? 10L : Math.min(q.getSize(), 100L);

        LambdaQueryWrapper<Transaction> wrapper = buildFilter(q, familyId);
        wrapper.orderByDesc(Transaction::getBizDate).orderByDesc(Transaction::getId);

        Page<Transaction> p = transactionMapper.selectPage(new Page<>(pageNo, size), wrapper);
        return PageResult.of(buildVOList(p.getRecords()), p.getTotal(), p.getCurrent(), p.getSize());
    }

    @Override
    public TransactionExportVO export(TransactionQuery q) {
        Long familyId = scope.familyId();
        LambdaQueryWrapper<Transaction> wrapper = buildFilter(q, familyId);
        // 银行流水习惯：日期升序；LIMIT 常量无注入风险，仅做超限兜底
        wrapper.orderByAsc(Transaction::getBizDate).orderByAsc(Transaction::getId)
                .last("LIMIT " + (EXPORT_LIMIT + 1));
        List<Transaction> records = transactionMapper.selectList(wrapper);
        if (records.size() > EXPORT_LIMIT) {
            throw new BizException(400, "查询结果超过 " + EXPORT_LIMIT + " 条，请缩小时间范围后再导出");
        }

        StringBuilder sb = new StringBuilder();
        // UTF-8 BOM：Excel 直接打开识别为 UTF-8，中文不乱码
        sb.append('﻿');
        sb.append("日期,类型,分类,金额(元),经手人,商家,片区,支付方式,标签,备注\r\n");
        for (TransactionVO vo : buildVOList(records)) {
            sb.append(vo.getBizDate()).append(',');
            sb.append(vo.getType() == 1 ? "收入" : "支出").append(',');
            sb.append(csvCell(vo.getCategoryName())).append(',');
            sb.append(vo.getAmount() == null ? "" : vo.getAmount().toPlainString()).append(',');
            sb.append(csvCell(vo.getMemberName() == null ? "家庭" : vo.getMemberName())).append(',');
            sb.append(csvCell(vo.getMerchant())).append(',');
            sb.append(csvCell(vo.getRegion())).append(',');
            sb.append(csvCell(vo.getPaymentMethod())).append(',');
            sb.append(csvCell(vo.getTags())).append(',');
            sb.append(csvCell(vo.getNote())).append('\r').append('\n');
        }

        TransactionExportVO vo = new TransactionExportVO();
        vo.setCount((long) records.size());
        vo.setCsv(sb.toString());
        return vo;
    }

    /** 筛选条件抽取（分页 / 导出共用，保证口径一致） */
    private LambdaQueryWrapper<Transaction> buildFilter(TransactionQuery q, Long familyId) {
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
        return wrapper;
    }

    /** CSV 单元格转义 + Excel 公式注入防护（= + - @ 开头时前置 '，使其按纯文本展示） */
    private String csvCell(String s) {
        if (s == null) {
            return "";
        }
        String v = s.trim();
        if (!v.isEmpty() && "+-@=".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\r") || v.contains("\n")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
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
        Long familyId = scope.familyId();

        // 对「最终生效」的 (type, categoryId) 组合做一致性校验：
        // 只要任一字段有变更就重验（防止把收入记录改成支出、或挂到类型错配的分类下）
        Integer finalType = dto.getType() != null ? dto.getType() : t.getType();
        Long finalCategoryId = dto.getCategoryId() != null ? dto.getCategoryId() : t.getCategoryId();
        if (!Objects.equals(finalType, t.getType()) || !Objects.equals(finalCategoryId, t.getCategoryId())) {
            validateCategory(familyId, finalCategoryId, finalType);
        }
        // 成员有变更（含显式置空 = 改回「家庭整体」）时校验归属
        if (!Objects.equals(t.getMemberId(), dto.getMemberId())) {
            validateMember(familyId, dto.getMemberId());
        }
        // 显式 set（允许 null）：可清空商家/备注/标签等可空字段，
        // 修复 MyBatis-Plus updateById 默认跳过 null 字段导致「清空不生效」的问题
        LambdaUpdateWrapper<Transaction> uw = new LambdaUpdateWrapper<>();
        uw.eq(Transaction::getId, id).eq(Transaction::getFamilyId, familyId);
        uw.set(Transaction::getType, finalType);
        uw.set(Transaction::getCategoryId, finalCategoryId);
        if (dto.getAmount() != null) {
            uw.set(Transaction::getAmount, dto.getAmount());
        }
        if (dto.getBizDate() != null) {
            uw.set(Transaction::getBizDate, dto.getBizDate());
        }
        uw.set(Transaction::getMemberId, dto.getMemberId());
        uw.set(Transaction::getMerchant, trimToNull(dto.getMerchant()));
        uw.set(Transaction::getRegion, trimToNull(dto.getRegion()));
        uw.set(Transaction::getTags, trimToNull(dto.getTags()));
        uw.set(Transaction::getPaymentMethod, trimToNull(dto.getPaymentMethod()));
        uw.set(Transaction::getImage, trimToNull(dto.getImage()));
        uw.set(Transaction::getNote, trimToNull(dto.getNote()));
        transactionMapper.update(null, uw);
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

    /** DTO → 实体（仅用于 create：空串归一为 null 便于前端回显；update 用显式 set 支持清空） */
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

    /** 收集分类 id 及其全部子孙 id：父分类筛选必含父自身，
     *  与统计钻取/预算执行的「自身+全子孙」口径一致（防止父类下历史流水被静默漏掉）；
     *  历史流水可能挂在停用子分类上，故含全部状态 */
    private void collectChildIds(Long familyId, Long categoryId, List<Long> out) {
        out.add(categoryId);
        List<Category> all = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().eq(Category::getFamilyId, familyId));
        collectDescendants(categoryId, all, out);
    }

    private void collectDescendants(Long parentId, List<Category> all, List<Long> out) {
        for (Category c : all) {
            if (Objects.equals(c.getParentId(), parentId)) {
                out.add(c.getId());
                collectDescendants(c.getId(), all, out);
            }
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
        List<Long> createdByIds = records.stream()
                .map(Transaction::getCreatedBy)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!createdByIds.isEmpty()) {
            for (User u : userMapper.selectBatchIds(createdByIds)) {
                userNameMap.put(u.getId(), u.getNickname() == null ? u.getUsername() : u.getNickname());
            }
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
