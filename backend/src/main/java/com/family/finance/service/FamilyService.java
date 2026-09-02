package com.family.finance.service;

import com.family.finance.dto.FamilyUpdateDTO;
import com.family.finance.entity.Family;

/**
 * 家庭信息：查看 / 修改
 */
public interface FamilyService {

    /** 当前家庭信息 */
    Family get();

    /** 修改家庭名称/描述（仅管理员） */
    void update(FamilyUpdateDTO dto);
}
