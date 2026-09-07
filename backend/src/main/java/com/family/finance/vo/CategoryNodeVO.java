package com.family.finance.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 分类树节点（含停用分类，status=0 由前端置灰）
 */
@Data
public class CategoryNodeVO {

    private Long id;
    private Long parentId;
    /** 类型: 1 收入 / 2 支出 */
    private Integer type;
    private String name;
    private String icon;
    private Integer sortOrder;
    /** 是否系统内置: 1 是(不可删) 0 用户自定义 */
    private Integer isSystem;
    /** 状态: 1 启用 0 停用 */
    private Integer status;

    private List<CategoryNodeVO> children = new ArrayList<>();
}
