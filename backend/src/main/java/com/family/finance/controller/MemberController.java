package com.family.finance.controller;

import com.family.finance.common.Result;
import com.family.finance.dto.MemberDTO;
import com.family.finance.entity.FamilyMember;
import com.family.finance.service.FamilyMemberService;
import jakarta.validation.Valid;
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
 * 家庭成员接口（管理需 ADMIN，记账数据可见性为家庭共享）
 */
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final FamilyMemberService memberService;

    /** 成员列表 */
    @GetMapping
    public Result<List<FamilyMember>> list() {
        return Result.ok(memberService.list());
    }

    /** 新增成员（仅管理员） */
    @PostMapping
    public Result<FamilyMember> add(@Valid @RequestBody MemberDTO dto) {
        return Result.ok(memberService.add(dto));
    }

    /** 修改成员（仅管理员） */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody MemberDTO dto) {
        memberService.update(id, dto);
        return Result.ok();
    }

    /** 删除成员（仅管理员；户主/有流水记录者禁删） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        memberService.delete(id);
        return Result.ok();
    }
}
