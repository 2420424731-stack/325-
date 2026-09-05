package com.family.finance.service;

import com.family.finance.dto.AssetDTO;
import com.family.finance.dto.AssetPositionDTO;
import com.family.finance.dto.PositionPriceDTO;
import com.family.finance.vo.AssetSummaryVO;
import com.family.finance.vo.AssetVO;

import java.util.List;

/**
 * 家庭资产管理（设计文档 F9）：资产登记、股票/基金持仓、总资产统计
 */
public interface AssetService {

    /** 资产列表（含持仓明细） */
    List<AssetVO> list();

    /** 资产汇总（总资产/按类型分布/净资产需配合贷款，此处只算资产侧） */
    AssetSummaryVO summary();

    AssetVO create(AssetDTO dto);

    AssetVO update(Long id, AssetDTO dto);

    void delete(Long id);

    /** 新增持仓（仅股票基金类资产） */
    AssetVO addPosition(Long assetId, AssetPositionDTO dto);

    /** 修改持仓 */
    AssetVO updatePosition(Long positionId, AssetPositionDTO dto);

    /** 删除持仓 */
    AssetVO deletePosition(Long positionId);

    /** 更新持仓现价（设计文档 6.3：PUT /api/assets/positions/{id}/price） */
    AssetVO updatePrice(Long positionId, PositionPriceDTO dto);
}
