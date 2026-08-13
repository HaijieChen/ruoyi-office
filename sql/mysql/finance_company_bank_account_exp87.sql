-- EXP-87 P1：公司银行账户主数据（挂靠组织架构公司，禁止双写公司主体）
-- 幂等：CREATE IF NOT EXISTS + 菜单/权限 NOT EXISTS

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `finance_company_bank_account` (
    `id`                       bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `entity_company_dept_id`   bigint NOT NULL COMMENT '主体公司=system_dept.id（orgType=公司）',
    `account_name`             varchar(128)  NOT NULL COMMENT '账户名称',
    `bank_name`                varchar(255)  NOT NULL COMMENT '开户行',
    `account_holder`           varchar(255)  NOT NULL COMMENT '户名',
    `account_no`               varchar(128)  NOT NULL COMMENT '银行账号（完整；展示脱敏）',
    `account_type`             varchar(32)   DEFAULT NULL COMMENT '账户类型 BASIC/GENERAL/SPECIAL 等',
    `currency`                 varchar(16)   NOT NULL DEFAULT 'CNY' COMMENT '币种 CNY/USD/HKD',
    `status`                   tinyint       NOT NULL DEFAULT 0 COMMENT '0 启用 / 1 停用',
    `remark`                   varchar(500)  DEFAULT NULL COMMENT '备注',
    `creator`                  varchar(64)   DEFAULT '' COMMENT '创建者',
    `create_time`              datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`                  varchar(64)   DEFAULT '' COMMENT '更新者',
    `update_time`              datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                  bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`                bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_fcba_entity_company` (`entity_company_dept_id`),
    KEY `idx_fcba_status` (`status`),
    KEY `idx_fcba_tenant` (`tenant_id`)
) ENGINE=InnoDB COMMENT='公司银行账户主数据（FK→组织公司）';

-- 菜单：挂财务目录；维护角色 finance_admin；出纳只读选账户 simple-list
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '公司银行账户', '', 2, 26,
       (SELECT `id` FROM `system_menu` WHERE `deleted` = b'0' AND `type` = 1 AND `parent_id` = 0 AND `path` IN ('finance', '/finance') LIMIT 1),
       'company-bank-account', 'ep:wallet', 'finance/company-bank-account/index', 'FinanceCompanyBankAccount',
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'finance/company-bank-account/index'
);

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT btn.name, btn.permission, 3, btn.sort, parent.id, '', '', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM (
    SELECT '公司银行账户查询' AS name, 'finance:company-bank-account:query' AS permission, 10 AS sort
    UNION ALL SELECT '公司银行账户创建', 'finance:company-bank-account:create', 11
    UNION ALL SELECT '公司银行账户更新', 'finance:company-bank-account:update', 12
) btn
CROSS JOIN (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'finance/company-bank-account/index' LIMIT 1
) parent
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu` m
    WHERE m.`deleted` = b'0' AND m.`permission` = btn.permission
);

-- simple-list 挂财务管理目录，便于出纳无维护页仍能拿权限
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '公司银行账户选择列表', 'finance:company-bank-account:simple-list', 3, 100, fin.id, '', '', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `type` = 1 AND `parent_id` = 0 AND `path` IN ('finance', '/finance') LIMIT 1
) fin
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu` m
    WHERE m.`deleted` = b'0' AND m.`permission` = 'finance:company-bank-account:simple-list'
);

-- finance_admin：页面 + 维护 + simple-list
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0' AND r.`code` = 'finance_admin' AND r.`tenant_id` = 1
  AND m.`deleted` = b'0'
  AND (
        m.`component` = 'finance/company-bank-account/index'
     OR m.`permission` IN (
            'finance:company-bank-account:query',
            'finance:company-bank-account:create',
            'finance:company-bank-account:update',
            'finance:company-bank-account:simple-list'
        )
  )
  AND NOT EXISTS (
      SELECT 1 FROM `system_role_menu` rm
      WHERE rm.`role_id` = r.`id` AND rm.`menu_id` = m.`id` AND rm.`tenant_id` = 1 AND rm.`deleted` = b'0'
  );

-- payment_cashier：仅 simple-list（出纳只读选）
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0' AND r.`code` = 'payment_cashier' AND r.`tenant_id` = 1
  AND m.`deleted` = b'0'
  AND m.`permission` = 'finance:company-bank-account:simple-list'
  AND NOT EXISTS (
      SELECT 1 FROM `system_role_menu` rm
      WHERE rm.`role_id` = r.`id` AND rm.`menu_id` = m.`id` AND rm.`tenant_id` = 1 AND rm.`deleted` = b'0'
  );
