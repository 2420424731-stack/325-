package com.family.finance.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.family.finance.common.BizException;
import com.family.finance.entity.FamilyMember;
import com.family.finance.mapper.FamilyMemberMapper;
import com.family.finance.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 家庭作用域服务：从当前登录态解析「用户 id / 所属家庭 id / 角色」。
 * 对应设计文档 6.4 数据隔离要求：所有查询强制带 family_id 条件，
 * 此处统一收口，避免各 Service 重复从 user → member → family 的解析逻辑。
 */
@Service
@RequiredArgsConstructor
public class FamilyScopeService {

    private final FamilyMemberMapper familyMemberMapper;
    private final UserMapper userMapper;

    /** 当前登录用户 id（拦截器已保证登录态） */
    public Long userId() {
        return StpUtil.getLoginIdAsLong();
    }

    /** 当前用户所属家庭 id（注册即建家，取绑定账号的第一个成员） */
    public Long familyId() {
        FamilyMember member = familyMemberMapper.selectOne(
                new LambdaQueryWrapper<FamilyMember>().eq(FamilyMember::getUserId, userId())
                        .orderByAsc(FamilyMember::getId).last("LIMIT 1"));
        if (member == null) {
            throw new BizException(403, "账号未加入任何家庭");
        }
        return member.getFamilyId();
    }

    /** 当前用户是否家庭管理员（角色 ADMIN，注册户主） */
    public boolean isAdmin() {
        return "ADMIN".equals(userMapper.selectById(userId()).getRole());
    }

    /** 管理操作前置校验：非管理员直接拒绝 */
    public void requireAdmin() {
        if (!isAdmin()) {
            throw new BizException(403, "仅家庭管理员可执行该操作");
        }
    }
}
