package com.family.finance.service.impl;

import com.family.finance.common.BizException;
import com.family.finance.dto.FamilyUpdateDTO;
import com.family.finance.entity.Family;
import com.family.finance.mapper.FamilyMapper;
import com.family.finance.service.FamilyScopeService;
import com.family.finance.service.FamilyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 家庭信息服务：家庭名称/描述维护
 */
@Service
@RequiredArgsConstructor
public class FamilyServiceImpl implements FamilyService {

    private final FamilyMapper familyMapper;
    private final FamilyScopeService scope;

    @Override
    public Family get() {
        return familyMapper.selectById(scope.familyId());
    }

    @Override
    public void update(FamilyUpdateDTO dto) {
        scope.requireAdmin();
        Family family = familyMapper.selectById(scope.familyId());
        if (family == null) {
            throw new BizException("家庭不存在");
        }
        family.setName(dto.getName());
        family.setDescription(dto.getDescription());
        familyMapper.updateById(family);
    }
}
