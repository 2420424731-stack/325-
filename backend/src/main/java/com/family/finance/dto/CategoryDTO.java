package com.family.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 分类新增/修改请求。
 * 修改时 type 必须与原有类型一致（不支持改类型），parentId 不支持变更（v1 不提供移动，删除重建即可）
 */
@Data
public class CategoryDTO {

    /** 类型: 1 收入 / 2 支出 */
    @NotNull(message = "分类类型不能为空")
    private Integer type;

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称最长 50 字符")
    private String name;

    /** 父分类 id, 0=顶级（null 默认 0） */
    private Long parentId;

    @Size(max = 50, message = "图标标识最长 50 字符")
    private String icon;

    /** 排序（null 默认 0） */
    private Integer sortOrder;

    /** 状态: 1 启用 0 停用（仅修改时生效，null=不变） */
    private Integer status;
}
