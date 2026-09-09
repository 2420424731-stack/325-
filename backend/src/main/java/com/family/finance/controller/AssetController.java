package com.family.finance.controller;

import com.family.finance.common.Result;
import com.family.finance.dto.AssetDTO;
import com.family.finance.service.AssetService;
import com.family.finance.vo.AssetSummaryVO;
import com.family.finance.vo.AssetVO;
import org.springframework.validation.annotation.Validated;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 资产管理接口（设计文档 F9：资产登记/汇总，管理需 ADMIN）
 */
@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    /** 资产列表 */
    @GetMapping
    public Result<List<AssetVO>> list() {
        return Result.ok(assetService.list());
    }

    /** 资产汇总（总资产/总贷款/净资产/按类型分布） */
    @GetMapping("/summary")
    public Result<AssetSummaryVO> summary() {
        return Result.ok(assetService.summary());
    }

    /** 新增资产（仅管理员） */
    @PostMapping
    public Result<AssetVO> create(@Validated(AssetDTO.Create.class) @RequestBody AssetDTO dto) {
        return Result.ok(assetService.create(dto));
    }

    /** 修改资产（仅管理员；v1 不支持改类型） */
    @PutMapping("/{id}")
    public Result<AssetVO> update(@PathVariable Long id,
                               @Validated(AssetDTO.Update.class) @RequestBody AssetDTO dto) {
        return Result.ok(assetService.update(id, dto));
    }

    /** 删除资产（仅管理员） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        assetService.delete(id);
        return Result.ok();
    }
}
