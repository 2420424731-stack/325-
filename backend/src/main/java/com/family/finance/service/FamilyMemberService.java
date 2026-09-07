package com.family.finance.service;

import com.family.finance.dto.MemberDTO;
import com.family.finance.entity.FamilyMember;

import java.util.List;

/**
 * 家庭成员管理：列表 / 新增 / 修改 / 删除
 */
public interface FamilyMemberService {

    /** 当前家庭全部成员（含停用，按 sortOrder 排序） */
    List<FamilyMember> list();

    /** 新增成员（仅管理员） */
    FamilyMember add(MemberDTO dto);

    /** 修改成员（仅管理员） */
    void update(Long id, MemberDTO dto);

    /** 删除成员（仅管理员；户主/自己/有流水记录者禁删） */
    void delete(Long id);
}
