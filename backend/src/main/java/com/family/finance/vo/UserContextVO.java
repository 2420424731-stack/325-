package com.family.finance.vo;

import com.family.finance.entity.Family;
import com.family.finance.entity.FamilyMember;
import lombok.Data;

import java.util.List;

/**
 * 当前登录用户上下文（/api/auth/me 响应）：用户 + 家庭 + 成员列表
 */
@Data
public class UserContextVO {

    private UserVO user;
    private Family family;
    private List<FamilyMember> members;
}
