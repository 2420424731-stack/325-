package com.family.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.family.finance.common.BizException;
import com.family.finance.dto.AssetDTO;
import com.family.finance.entity.Asset;
import com.family.finance.entity.Loan;
import com.family.finance.mapper.AssetMapper;
import com.family.finance.mapper.LoanMapper;
import com.family.finance.service.AssetService;
import com.family.finance.service.FamilyScopeService;
import com.family.finance.vo.AssetSummaryVO;
import com.family.finance.vo.AssetVO;
import com.family.finance.vo.NameStatVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 资产管理实现。
 * - 资产登记（房产/存款/汽车/其他）与总资产统计；资产增删改仅管理员，查询家庭内共享
 */
@Service
@RequiredArgsConstructor
public class AssetServiceImpl implements AssetService {

    /** 合法资产类型（与 init.sql 注释一致） */
    private static final Set<String> ASSET_TYPES = Set.of("房产", "存款", "汽车", "其他");

    private final AssetMapper assetMapper;
    private final LoanMapper loanMapper;
    private final FamilyScopeService scope;

    @Override
    public List<AssetVO> list() {
        Long familyId = scope.familyId();
        List<Asset> assets = assetMapper.selectList(
                new LambdaQueryWrapper<Asset>()
                        .eq(Asset::getFamilyId, familyId)
                        .orderByAsc(Asset::getId));
        List<AssetVO> result = new ArrayList<>();
        for (Asset a : assets) {
            result.add(toVO(a));
        }
        return result;
    }

    @Override
    public AssetSummaryVO summary() {
        Long familyId = scope.familyId();
        List<Asset> assets = assetMapper.selectList(
                new LambdaQueryWrapper<Asset>().eq(Asset::getFamilyId, familyId));
        BigDecimal totalAssets = BigDecimal.ZERO;
        Map<String, NameStatVO> byType = new LinkedHashMap<>();
        for (Asset a : assets) {
            BigDecimal v = a.getValue() == null ? BigDecimal.ZERO : a.getValue();
            totalAssets = totalAssets.add(v);
            NameStatVO stat = byType.computeIfAbsent(a.getAssetType(), k -> {
                NameStatVO s = new NameStatVO();
                s.setName(k);
                s.setTotal(BigDecimal.ZERO);
                s.setCount(0L);
                return s;
            });
            stat.setTotal(stat.getTotal().add(v));
            stat.setCount(stat.getCount() + 1);
        }
        // 总贷款按剩余本金计（未填剩余本金按本金计），净资产 = 总资产 − 总贷款（设计文档 8.1）
        BigDecimal totalLoans = BigDecimal.ZERO;
        for (Loan l : loanMapper.selectList(
                new LambdaQueryWrapper<Loan>().eq(Loan::getFamilyId, familyId))) {
            BigDecimal rest = l.getRemainingPrincipal() != null ? l.getRemainingPrincipal() : l.getPrincipal();
            if (rest != null) {
                totalLoans = totalLoans.add(rest);
            }
        }
        AssetSummaryVO vo = new AssetSummaryVO();
        vo.setTotalAssets(totalAssets);
        vo.setTotalLoans(totalLoans);
        vo.setNetAssets(totalAssets.subtract(totalLoans));
        vo.setByType(new ArrayList<>(byType.values()));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssetVO create(AssetDTO dto) {
        scope.requireAdmin();
        checkType(dto.getAssetType());
        Asset a = new Asset();
        a.setFamilyId(scope.familyId());
        a.setAssetType(dto.getAssetType());
        a.setName(dto.getName());
        a.setValue(dto.getValue() == null ? BigDecimal.ZERO : dto.getValue());
        a.setPurchaseDate(dto.getPurchaseDate());
        a.setNote(dto.getNote());
        assetMapper.insert(a);
        return toVO(a);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssetVO update(Long id, AssetDTO dto) {
        scope.requireAdmin();
        Asset a = requireAsset(id);
        if (dto.getAssetType() != null && !a.getAssetType().equals(dto.getAssetType())) {
            throw new BizException(400, "v1 不支持修改资产类型，请删除后重建");
        }
        // 局部更新：仅覆盖已传字段，未传字段保持原值
        if (dto.getName() != null) {
            a.setName(dto.getName());
        }
        if (dto.getValue() != null) {
            a.setValue(dto.getValue());
        }
        if (dto.getPurchaseDate() != null) {
            a.setPurchaseDate(dto.getPurchaseDate());
        }
        if (dto.getNote() != null) {
            a.setNote(dto.getNote());
        }
        assetMapper.updateById(a);
        return toVO(a);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        scope.requireAdmin();
        requireAsset(id);
        assetMapper.deleteById(id);
    }

    // ================= 辅助 =================

    private AssetVO toVO(Asset a) {
        AssetVO vo = new AssetVO();
        vo.setId(a.getId());
        vo.setAssetType(a.getAssetType());
        vo.setName(a.getName());
        vo.setValue(a.getValue());
        vo.setPurchaseDate(a.getPurchaseDate());
        vo.setNote(a.getNote());
        return vo;
    }

    /** 资产存在且属于当前家庭（越权视为不存在，与收支模块口径一致） */
    private Asset requireAsset(Long id) {
        Asset a = assetMapper.selectById(id);
        if (a == null || !a.getFamilyId().equals(scope.familyId())) {
            throw new BizException(404, "资产不存在");
        }
        return a;
    }

    private void checkType(String type) {
        if (!ASSET_TYPES.contains(type)) {
            throw new BizException(400, "资产类型须为：房产/存款/汽车/其他");
        }
    }
}