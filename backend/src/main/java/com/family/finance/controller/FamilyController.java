package com.family.finance.controller;

import com.family.finance.common.Result;
import com.family.finance.dto.FamilyUpdateDTO;
import com.family.finance.entity.Family;
import com.family.finance.service.FamilyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 家庭信息接口
 */
@RestController
@RequestMapping("/api/family")
@RequiredArgsConstructor
public class FamilyController {

    private final FamilyService familyService;

    /** 当前家庭信息 */
    @GetMapping
    public Result<Family> get() {
        return Result.ok(familyService.get());
    }

    /** 修改家庭名称/描述（仅管理员） */
    @PutMapping
    public Result<Void> update(@Valid @RequestBody FamilyUpdateDTO dto) {
        familyService.update(dto);
        return Result.ok();
    }
}
