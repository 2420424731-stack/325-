package com.family.finance.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 家庭成员
 */
@Data
@TableName("family_member")
public class FamilyMember {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属家庭 */
    private Long familyId;

    /** 绑定账号 user.id（可空=不登录记账） */
    private Long userId;

    /** 成员姓名/称呼 */
    private String name;

    /** 关系: 户主/配偶/子女/父母/其他 */
    private String relation;

    /** 出生日期 */
    private LocalDate birthday;

    /** 排序 */
    private Integer sortOrder;

    /** 状态: 1 启用 0 停用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
