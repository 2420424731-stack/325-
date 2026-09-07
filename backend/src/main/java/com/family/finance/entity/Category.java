package com.family.finance.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收支分类（多级树形、可自定义）
 */
@Data
@TableName("category")
public class Category {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属家庭 */
    private Long familyId;

    /** 父分类 id, 0=顶级 */
    private Long parentId;

    /** 类型: 1 收入分类 / 2 支出分类 */
    private Integer type;

    /** 分类名称 */
    private String name;

    /** 图标标识 */
    private String icon;

    /** 排序 */
    private Integer sortOrder;

    /** 是否系统内置: 1 是(不可删) 0 用户自定义 */
    private Integer isSystem;

    /** 状态: 1 启用 0 停用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
