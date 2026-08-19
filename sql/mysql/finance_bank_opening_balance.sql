-- STORY-0010 U1：公司银行账户期初余额（每账户一条，二次写入 upsert）
-- 幂等：CREATE IF NOT EXISTS + 权限 NOT EXISTS
-- 页面菜单留给 U3

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `finance_bank_opening_balance` (
    `id`          bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `account_id`  bigint NOT NULL COMMENT '公司银行账户 finance_company_bank_account.id',
    `as_of_date`  date NOT NULL COMMENT '期初日期（启用月前一天）',
    `amount`      decimal(18,2) NOT NULL COMMENT '期初金额',
    `currency`    varchar(16) NOT NULL DEFAULT 'CNY' COMMENT '币种 CNY/USD/HKD',
    `creator`     varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`   bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_fbob_account_tenant_deleted` (`account_id`, `tenant_id`, `deleted`),
    KEY `idx_fbob_as_of_date` (`as_of_date`),
    KEY `idx_fbob_tenant` (`tenant_id`)
) ENGINE=InnoDB COMMENT='公司银行账户期初余额';

-- 权限按钮：挂财务目录；页面组件由 U3 补
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT btn.name, btn.permission, 3, btn.sort, fin.id, '', '', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM (
    SELECT '银行余额表查询' AS name, 'finance:report-bank-balance:query' AS permission, 110 AS sort
    UNION ALL SELECT '银行期初余额更新', 'finance:bank-opening:update', 111
) btn
CROSS JOIN (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `type` = 1 AND `parent_id` = 0 AND `path` IN ('finance', '/finance') LIMIT 1
) fin
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu` m
    WHERE m.`deleted` = b'0' AND m.`permission` = btn.permission
);

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0' AND r.`code` = 'finance_admin' AND r.`tenant_id` = 1
  AND m.`deleted` = b'0'
  AND m.`permission` IN (
        'finance:report-bank-balance:query',
        'finance:bank-opening:update'
  )
  AND NOT EXISTS (
      SELECT 1 FROM `system_role_menu` rm
      WHERE rm.`role_id` = r.`id` AND rm.`menu_id` = m.`id` AND rm.`tenant_id` = 1 AND rm.`deleted` = b'0'
  );
