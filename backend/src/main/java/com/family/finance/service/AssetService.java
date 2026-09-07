package com.family.finance.service;

import com.family.finance.dto.AssetDTO;
import com.family.finance.vo.AssetSummaryVO;
import com.family.finance.vo.AssetVO;

import java.util.List;

/**
 * 家庭资产管理（设计文档 F9）：资产登记与总资产统计
 */
public interface AssetService {

    /** 资产列表 */
    List<AssetVO> list();

    /** 资产汇总（总资产/按类型分布/净资产需配合贷款，此处只算资产侧） */
    AssetSummaryVO summary();

    AssetVO create(AssetDTO dto);

    AssetVO update(Long id, AssetDTO dto);

    void delete(Long id);
}
