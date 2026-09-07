package com.family.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.family.finance.common.BizException;
import com.family.finance.dto.MemberDTO;
import com.family.finance.entity.FamilyMember;
import com.family.finance.entity.Transaction;
import com.family.finance.mapper.FamilyMemberMapper;
import com.family.finance.mapper.TransactionMapper;
import com.family.finance.service.FamilyMemberService;
import com.family.finance.service.FamilyScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 家庭成员管理：一个家庭一个户主（注册自动创建），其余成员由管理员维护。
 * 删除约束：户主不可删、自己不可删、有收支流水引用不可删（可停用代替）。
 */
@Service
@RequiredArgsConstructor
public class FamilyMemberServiceImpl implements FamilyMemberService {

    private static final String RELATION_OWNER = "户主";

    private final FamilyMemberMapper memberMapper;
    private final TransactionMapper transactionMapper;
    private final FamilyScopeService scope;

    @Override
    public List<FamilyMember> list() {
        return memberMapper.selectList(
                new LambdaQueryWrapper<FamilyMember>()
                        .eq(FamilyMember::getFamilyId, scope.familyId())
                        .orderByAsc(FamilyMember::getSortOrder)
                        .orderByAsc(FamilyMember::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FamilyMember add(MemberDTO dto) {
        scope.requireAdmin();
        Long familyId = scope.familyId();
        // 一个家庭仅允许一个户主（注册时已自动创建）
        if (RELATION_OWNER.equals(dto.getRelation())) {
            Long ownerCount = memberMapper.selectCount(
                    new LambdaQueryWrapper<FamilyMember>()
                            .eq(FamilyMember::getFamilyId, familyId)
                            .eq(FamilyMember::getRelation, RELATION_OWNER)
                            .eq(FamilyMember::getStatus, 1));
            if (ownerCount > 0) {
                throw new BizException(400, "该家庭已有户主，不能重复添加");
            }
        }
        FamilyMember member = new FamilyMember();
        member.setFamilyId(familyId);
        member.setName(dto.getName().trim());
        member.setRelation(dto.getRelation());
        member.setBirthday(dto.getBirthday());
        member.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        member.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        memberMapper.insert(member);
        return member;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, MemberDTO dto) {
        scope.requireAdmin();
        FamilyMember member = requireMember(id);
        member.setName(dto.getName().trim());
        member.setRelation(dto.getRelation());
        member.setBirthday(dto.getBirthday());
        member.setSortOrder(dto.getSortOrder());
        member.setStatus(dto.getStatus());
        memberMapper.updateById(member);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        scope.requireAdmin();
        FamilyMember member = requireMember(id);
        if (RELATION_OWNER.equals(member.getRelation())) {
            throw new BizException(400, "户主成员不可删除");
        }
        if (member.getUserId() != null && member.getUserId().equals(scope.userId())) {
            throw new BizException(400, "不能删除自己绑定的成员记录");
        }
        Long used = transactionMapper.selectCount(
                new LambdaQueryWrapper<Transaction>()
                        .eq(Transaction::getFamilyId, scope.familyId())
                        .eq(Transaction::getMemberId, id));
        if (used > 0) {
            throw new BizException(400, "该成员名下已有收支记录，不能删除，可改为停用");
        }
        memberMapper.deleteById(id);
    }

    /** 校验成员属于当前家庭，否则视为不存在（防越权） */
    private FamilyMember requireMember(Long id) {
        FamilyMember member = memberMapper.selectOne(
                new LambdaQueryWrapper<FamilyMember>()
                        .eq(FamilyMember::getId, id)
                        .eq(FamilyMember::getFamilyId, scope.familyId()));
        if (member == null) {
            throw new BizException("成员不存在");
        }
        return member;
    }
}
