-- =====================================================================
-- 《管家婆─家庭收支管理系统》数据库初始化脚本
-- 版本: v1.0    日期: 2026-09-01    编码: utf8mb4
-- 说明: 建库 + 建表（含索引与字段注释），与《系统设计方案》5.2 节一致
-- 执行: mysql -u root -p < init.sql
-- =====================================================================

CREATE DATABASE IF NOT EXISTS `family_finance`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE `family_finance`;

-- ---------------------------------------------------------------------
-- 1. user 用户表（登录账号）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username`    VARCHAR(50)  NOT NULL COMMENT '登录名',
  `password`    VARCHAR(100) NOT NULL COMMENT '密码(BCrypt 密文)',
  `nickname`    VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
  `avatar`      VARCHAR(255) DEFAULT NULL COMMENT '头像地址',
  `role`        VARCHAR(20)  NOT NULL DEFAULT 'MEMBER' COMMENT '角色: ADMIN 家庭管理员 / MEMBER',
  `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1 正常 0 禁用',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB COMMENT = '用户表';

-- ---------------------------------------------------------------------
-- 2. family 家庭表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `family`;
CREATE TABLE `family` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`        VARCHAR(50)  NOT NULL COMMENT '家庭名称',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '描述',
  `created_by`  BIGINT       DEFAULT NULL COMMENT '创建人 user.id',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB COMMENT = '家庭表';

-- ---------------------------------------------------------------------
-- 3. family_member 家庭成员表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `family_member`;
CREATE TABLE `family_member` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `family_id`   BIGINT       NOT NULL COMMENT '所属家庭',
  `user_id`     BIGINT       DEFAULT NULL COMMENT '绑定账号 user.id（可空=不登录记账）',
  `name`        VARCHAR(50)  NOT NULL COMMENT '成员姓名/称呼',
  `relation`    VARCHAR(20)  DEFAULT NULL COMMENT '关系: 户主/配偶/子女/父母/其他',
  `birthday`    DATE         DEFAULT NULL COMMENT '出生日期',
  `sort_order`  INT          NOT NULL DEFAULT 0 COMMENT '排序',
  `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1 启用 0 停用',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_family_id` (`family_id`)
) ENGINE = InnoDB COMMENT = '家庭成员表';

-- ---------------------------------------------------------------------
-- 4. category 收支分类表（多级树形、可自定义）
--    内置分类在家庭注册初始化时由后端复制生成；删除用逻辑删除可由
--    status=0 实现，但内置分类不可删、有子分类/流水时禁止删除（后端校验）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `category`;
CREATE TABLE `category` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `family_id`   BIGINT       NOT NULL COMMENT '所属家庭',
  `parent_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '父分类 id, 0=顶级',
  `type`        TINYINT      NOT NULL COMMENT '类型: 1 收入分类 / 2 支出分类',
  `name`        VARCHAR(50)  NOT NULL COMMENT '分类名称',
  `icon`        VARCHAR(50)  DEFAULT NULL COMMENT '图标标识',
  `sort_order`  INT          NOT NULL DEFAULT 0 COMMENT '排序',
  `is_system`   TINYINT      NOT NULL DEFAULT 0 COMMENT '是否系统内置: 1 是(不可删) 0 用户自定义',
  `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1 启用 0 停用',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_family_type_parent` (`family_id`, `type`, `parent_id`)
) ENGINE = InnoDB COMMENT = '收支分类表';

-- ---------------------------------------------------------------------
-- 5. transaction 收支记录表（核心表）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `transaction`;
CREATE TABLE `transaction` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `family_id`      BIGINT        NOT NULL COMMENT '所属家庭',
  `member_id`      BIGINT        DEFAULT NULL COMMENT '经手成员 family_member.id（空=家庭整体）',
  `type`           TINYINT       NOT NULL COMMENT '类型: 1 收入 / 2 支出',
  `category_id`    BIGINT        NOT NULL COMMENT '归属分类(叶子分类)',
  `amount`         DECIMAL(12,2) NOT NULL COMMENT '金额(>0)',
  `biz_date`       DATE          NOT NULL COMMENT '业务发生日期',
  `merchant`       VARCHAR(100)  DEFAULT NULL COMMENT '商家/对方',
  `region`         VARCHAR(50)   DEFAULT NULL COMMENT '消费片区',
  `tags`           VARCHAR(100)  DEFAULT NULL COMMENT '标签(逗号分隔), 如: 礼尚往来,生日',
  `payment_method` VARCHAR(20)   DEFAULT NULL COMMENT '支付方式: 支付宝/微信/银行卡/现金/其他',
  `image`          VARCHAR(255)  DEFAULT NULL COMMENT '凭证图片地址',
  `note`           VARCHAR(255)  DEFAULT NULL COMMENT '备注',
  `created_by`     BIGINT        DEFAULT NULL COMMENT '录入人 user.id',
  `deleted`        TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0 正常 1 已删',
  `created_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_family_bizdate` (`family_id`, `biz_date`),
  KEY `idx_family_type_cat` (`family_id`, `type`, `category_id`),
  KEY `idx_family_member` (`family_id`, `member_id`),
  KEY `idx_merchant` (`merchant`),
  KEY `idx_region` (`region`)
) ENGINE = InnoDB COMMENT = '收支记录表';

-- ---------------------------------------------------------------------
-- 6. budget 预算表（拓展）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `budget`;
CREATE TABLE `budget` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `family_id`     BIGINT        NOT NULL COMMENT '所属家庭',
  `category_id`   BIGINT        DEFAULT NULL COMMENT '预算分类 category.id（空=家庭总预算）',
  `budget_month`  CHAR(7)       NOT NULL COMMENT '预算月份, 如 2026-09',
  `amount`        DECIMAL(12,2) NOT NULL COMMENT '预算金额',
  `created_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_family_cat_month` (`family_id`, `category_id`, `budget_month`)
) ENGINE = InnoDB COMMENT = '预算表';

-- ---------------------------------------------------------------------
-- 7. asset 资产表（拓展）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `asset`;
CREATE TABLE `asset` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `family_id`     BIGINT        NOT NULL COMMENT '所属家庭',
  `asset_type`    VARCHAR(20)   NOT NULL COMMENT '类型: 房产/股票基金/存款/汽车/其他',
  `name`          VARCHAR(100)  NOT NULL COMMENT '资产名称',
  `value`         DECIMAL(14,2) NOT NULL COMMENT '当前估值',
  `purchase_date` DATE          DEFAULT NULL COMMENT '购置日期',
  `note`          VARCHAR(255)  DEFAULT NULL COMMENT '备注',
  `created_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_family_id` (`family_id`)
) ENGINE = InnoDB COMMENT = '资产表';

-- ---------------------------------------------------------------------
-- 8. asset_position 股票/基金持仓表（拓展）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `asset_position`;
CREATE TABLE `asset_position` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `asset_id`      BIGINT        NOT NULL COMMENT '所属资产 asset.id（asset_type=股票基金）',
  `code`          VARCHAR(20)   DEFAULT NULL COMMENT '证券代码, 如 600519',
  `name`          VARCHAR(50)   DEFAULT NULL COMMENT '证券名称',
  `shares`        INT           NOT NULL COMMENT '持仓数量',
  `cost_price`    DECIMAL(12,4) DEFAULT NULL COMMENT '成本价',
  `current_price` DECIMAL(12,4) DEFAULT NULL COMMENT '现价（手工更新）',
  `updated_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '估值更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_asset_id` (`asset_id`)
) ENGINE = InnoDB COMMENT = '股票基金持仓表';

-- ---------------------------------------------------------------------
-- 9. loan 贷款表（拓展：房贷/车贷）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `loan`;
CREATE TABLE `loan` (
  `id`                 BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `family_id`          BIGINT        NOT NULL COMMENT '所属家庭',
  `name`               VARCHAR(50)   NOT NULL COMMENT '贷款名称: 房贷/车贷/消费贷',
  `principal`          DECIMAL(14,2) NOT NULL COMMENT '贷款本金',
  `annual_rate`        DECIMAL(6,4)  DEFAULT NULL COMMENT '年利率, 如 0.0380',
  `term_months`        INT           DEFAULT NULL COMMENT '期数(月)',
  `start_date`         DATE          DEFAULT NULL COMMENT '起贷日期',
  `repayment_type`     VARCHAR(20)   NOT NULL DEFAULT 'equal_installment' COMMENT '还款方式: equal_installment 等额本息 / equal_principal 等额本金',
  `monthly_payment`    DECIMAL(14,2) DEFAULT NULL COMMENT '月供(等额本息固定/等额本金为首月)',
  `remaining_principal` DECIMAL(14,2) DEFAULT NULL COMMENT '剩余本金(按月更新)',
  `lender`             VARCHAR(50)   DEFAULT NULL COMMENT '贷款机构',
  `created_at`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_family_id` (`family_id`)
) ENGINE = InnoDB COMMENT = '贷款表';

-- =====================================================================
-- 初始化完成：共 9 张表
-- 备注: 系统内置收支分类、演示数据见 seed 脚本（编码阶段提供，随家庭注册初始化）
-- =====================================================================
