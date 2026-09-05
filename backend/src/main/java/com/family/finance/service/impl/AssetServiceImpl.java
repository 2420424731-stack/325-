package com.family.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.family.finance.common.BizException;
import com.family.finance.dto.AssetDTO;
import com.family.finance.dto.AssetPositionDTO;
import com.family.finance.dto.PositionPriceDTO;
import com.family.finance.entity.Asset;
import com.family.finance.entity.AssetPosition;
import com.family.finance.entity.Loan;
import com.family.finance.mapper.AssetMapper;
import com.family.finance.mapper.AssetPositionMapper;
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
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 资产管理实现。
 * - 股票基金类资产估值 = Σ(持仓数量 × 现价)，持仓变化/现价更新时自动重算（设计文档 9.1）
 * - 资产增删改与持仓维护仅管理员；查询家庭内共享
 */
@Service
@RequiredArgsConstructor
public class AssetServiceImpl implements AssetService {

    /** 合法资产类型（与 init.sql 注释一致） */
    private static final Set<String> ASSET_TYPES = Set.of("房产", "股票基金", "存款", "汽车", "其他");
    private static final String STOCK_TYPE = "股票基金";

    private final AssetMapper assetMapper;
    private final AssetPositionMapper positionMapper;
    private final LoanMapper loanMapper;
    private final FamilyScopeService scope;

    @Override
    public List<AssetVO> list() {
        Long familyId = scope.familyId();
        List<Asset> assets = assetMapper.selectList(
                new LambdaQueryWrapper<Asset>()
                        .eq(Asset::getFamilyId, familyId)
                        .orderByAsc(Asset::getId));
        if (assets.isEmpty()) {
            return List.of();
        }
        // 一次性取出本家庭全部持仓，按资产分组
        Map<Long, List<AssetPosition>> byAsset = new LinkedHashMap<>();
        for (AssetPosition p : positionMapper.selectList(
                new LambdaQueryWrapper<AssetPosition>()
                        .in(AssetPosition::getAssetId, assets.stream().map(Asset::getId).toList()))) {
            byAsset.computeIfAbsent(p.getAssetId(), k -> new ArrayList<>()).add(p);
        }
        List<AssetVO> result = new ArrayList<>();
        for (Asset a : assets) {
            AssetVO vo = toVO(a, byAsset.getOrDefault(a.getId(), List.of()));
            result.add(vo);
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
        return toVO(a, List.of());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssetVO update(Long id, AssetDTO dto) {
        scope.requireAdmin();
        Asset a = requireAsset(id);
        if (dto.getAssetType() != null && !a.getAssetType().equals(dto.getAssetType())) {
            throw new BizException(400, "v1 不支持修改资产类型，请删除后重建");
        }
        a.setName(dto.getName());
        if (!STOCK_TYPE.equals(a.getAssetType())) {
            a.setValue(dto.getValue() == null ? BigDecimal.ZERO : dto.getValue());
        }
        a.setPurchaseDate(dto.getPurchaseDate());
        a.setNote(dto.getNote());
        assetMapper.updateById(a);
        // 股票基金类估值始终以持仓为准，防止手工 value 覆盖失真
        List<AssetPosition> positions = positionsOf(a.getId());
        if (STOCK_TYPE.equals(a.getAssetType()) && !positions.isEmpty()) {
            recomputeValue(a.getId());
            a = assetMapper.selectById(a.getId()); // 重算后取最新估值
        }
        return toVO(a, positions);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        scope.requireAdmin();
        requireAsset(id);
        positionMapper.delete(new LambdaQueryWrapper<AssetPosition>().eq(AssetPosition::getAssetId, id));
        assetMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssetVO addPosition(Long assetId, AssetPositionDTO dto) {
        scope.requireAdmin();
        Asset a = requireStockAsset(assetId);
        AssetPosition p = new AssetPosition();
        p.setAssetId(assetId);
        p.setCode(dto.getCode());
        p.setName(dto.getName());
        p.setShares(dto.getShares());
        p.setCostPrice(dto.getCostPrice());
        p.setCurrentPrice(dto.getCurrentPrice());
        positionMapper.insert(p);
        recomputeValue(assetId);
        return toVO(assetMapper.selectById(assetId), positionsOf(assetId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssetVO updatePosition(Long positionId, AssetPositionDTO dto) {
        scope.requireAdmin();
        AssetPosition p = requirePosition(positionId);
        p.setCode(dto.getCode());
        p.setName(dto.getName());
        p.setShares(dto.getShares());
        p.setCostPrice(dto.getCostPrice());
        p.setCurrentPrice(dto.getCurrentPrice());
        positionMapper.updateById(p);
        recomputeValue(p.getAssetId());
        return toVO(assetMapper.selectById(p.getAssetId()), positionsOf(p.getAssetId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssetVO deletePosition(Long positionId) {
        scope.requireAdmin();
        AssetPosition p = requirePosition(positionId);
        positionMapper.deleteById(positionId);
        recomputeValue(p.getAssetId());
        return toVO(assetMapper.selectById(p.getAssetId()), positionsOf(p.getAssetId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssetVO updatePrice(Long positionId, PositionPriceDTO dto) {
        scope.requireAdmin();
        AssetPosition p = requirePosition(positionId);
        p.setCurrentPrice(dto.getCurrentPrice());
        positionMapper.updateById(p);
        recomputeValue(p.getAssetId());
        return toVO(assetMapper.selectById(p.getAssetId()), positionsOf(p.getAssetId()));
    }

    // ================= 辅助 =================

    private AssetVO toVO(Asset a, List<AssetPosition> positions) {
        AssetVO vo = new AssetVO();
        vo.setId(a.getId());
        vo.setAssetType(a.getAssetType());
        vo.setName(a.getName());
        vo.setValue(a.getValue());
        vo.setPurchaseDate(a.getPurchaseDate());
        vo.setNote(a.getNote());
        if (STOCK_TYPE.equals(a.getAssetType())) {
            vo.setPositions(positions);
            BigDecimal market = BigDecimal.ZERO;
            for (AssetPosition p : positions) {
                BigDecimal price = p.getCurrentPrice() != null ? p.getCurrentPrice()
                        : (p.getCostPrice() != null ? p.getCostPrice() : BigDecimal.ZERO);
                market = market.add(price.multiply(BigDecimal.valueOf(p.getShares())));
            }
            vo.setMarketValue(market);
        }
        return vo;
    }

    /** 按持仓重算股票基金类资产估值：市值 = Σ(数量 × 现价)（设计文档 9.1） */
    private void recomputeValue(Long assetId) {
        BigDecimal market = BigDecimal.ZERO;
        for (AssetPosition p : positionsOf(assetId)) {
            BigDecimal price = p.getCurrentPrice() != null ? p.getCurrentPrice()
                    : (p.getCostPrice() != null ? p.getCostPrice() : BigDecimal.ZERO);
            market = market.add(price.multiply(BigDecimal.valueOf(p.getShares())));
        }
        Asset a = assetMapper.selectById(assetId);
        a.setValue(market);
        assetMapper.updateById(a);
    }

    private List<AssetPosition> positionsOf(Long assetId) {
        return positionMapper.selectList(
                new LambdaQueryWrapper<AssetPosition>()
                        .eq(AssetPosition::getAssetId, assetId)
                        .orderByAsc(AssetPosition::getId));
    }

    /** 资产存在且属于当前家庭（越权视为不存在，与收支模块口径一致） */
    private Asset requireAsset(Long id) {
        Asset a = assetMapper.selectById(id);
        if (a == null || !a.getFamilyId().equals(scope.familyId())) {
            throw new BizException("资产不存在");
        }
        return a;
    }

    private Asset requireStockAsset(Long id) {
        Asset a = requireAsset(id);
        if (!STOCK_TYPE.equals(a.getAssetType())) {
            throw new BizException(400, "仅「股票基金」类资产可维护持仓");
        }
        return a;
    }

    /** 持仓存在且其所属资产属于当前家庭 */
    private AssetPosition requirePosition(Long positionId) {
        AssetPosition p = positionMapper.selectById(positionId);
        if (p == null) {
            throw new BizException("持仓不存在");
        }
        requireAsset(p.getAssetId());
        return p;
    }

    private void checkType(String type) {
        if (!ASSET_TYPES.contains(type)) {
            throw new BizException(400, "资产类型须为：房产/股票基金/存款/汽车/其他");
        }
    }
}
