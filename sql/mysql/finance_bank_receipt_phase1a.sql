SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `finance_bank_receipt` (
    `id`                 bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `receipt_no`         varchar(64) NOT NULL COMMENT '到款流水号',
    `import_date`        date NOT NULL COMMENT '导入日期',
    `importer_id`        bigint NOT NULL COMMENT '导入人编号',
    `bank_account`       varchar(255) NOT NULL COMMENT '银行账户',
    `transaction_date`   datetime NOT NULL COMMENT '交易日期',
    `payer_name`         varchar(255) NOT NULL COMMENT '付款方名称',
    `payer_account`      varchar(255) DEFAULT NULL COMMENT '付款方账号',
    `transaction_amount` decimal(18,2) NOT NULL COMMENT '交易金额',
    `summary`            varchar(500) DEFAULT NULL COMMENT '摘要/附言',
    `bank_serial_no`     varchar(127) NOT NULL COMMENT '银行流水号',
    `claim_status`       tinyint NOT NULL DEFAULT 0 COMMENT '认领状态（0-待认领，1-部分认领，2-完全认领，3-已关闭）',
    `claimed_amount`     decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '已认领金额',
    `unclaimed_amount`   decimal(18,2) NOT NULL COMMENT '未认领金额',
    `creator`            varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time`        datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`            varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time`        datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`            bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`          bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_receipt_no_tenant_deleted` (`receipt_no`, `tenant_id`, `deleted`),
    UNIQUE KEY `uk_bank_serial_no_tenant_deleted` (`bank_serial_no`, `tenant_id`, `deleted`),
    KEY `idx_claim_status_unclaimed` (`claim_status`, `unclaimed_amount`),
    KEY `idx_transaction_date` (`transaction_date`)
) ENGINE=InnoDB COMMENT='财务银行到款记录';

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '财务管理', '', 1, 40, 0, '/finance', 'fa:money', NULL, NULL,
       0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `type` = 1 AND `parent_id` = 0 AND `path` IN ('finance', '/finance')
);

-- 兼容历史数据：相对 path `finance` 归一为绝对 path `/finance`（仅一级菜单）
UPDATE `system_menu`
SET `path` = '/finance', `update_time` = NOW()
WHERE `deleted` = b'0' AND `type` = 1 AND `parent_id` = 0 AND `path` = 'finance';

UPDATE `system_menu`
SET `name` = '财务管理', `sort` = 40, `parent_id` = 0, `path` = '/finance', `icon` = 'fa:money',
    `component` = NULL, `component_name` = NULL,
    `status` = 0, `visible` = b'1', `keep_alive` = b'1', `always_show` = b'1', `update_time` = NOW()
WHERE `deleted` = b'0' AND `type` = 1 AND `parent_id` = 0 AND `path` IN ('finance', '/finance');

UPDATE `system_menu`
SET `name` = 'ERP 财务管理', `update_time` = NOW()
WHERE `deleted` = b'0' AND `path` = 'finance' AND `parent_id` <> 0;

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '银行到款', '', 2, 1, parent_menu.id, 'receipt', 'fa:bank', 'finance/receipt/index', 'FinanceReceipt',
       0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
FROM (SELECT `id` FROM `system_menu` WHERE `deleted` = b'0' AND `type` = 1 AND `parent_id` = 0 AND `path` IN ('finance', '/finance') LIMIT 1) parent_menu
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'finance/receipt/index'
);

UPDATE `system_menu`
SET `name` = '银行到款',
    `parent_id` = (SELECT parent_menu.id FROM (SELECT `id` FROM `system_menu` WHERE `deleted` = b'0' AND `type` = 1 AND `parent_id` = 0 AND `path` IN ('finance', '/finance') LIMIT 1) parent_menu),
    `path` = 'receipt', `icon` = 'fa:bank', `component` = 'finance/receipt/index', `component_name` = 'FinanceReceipt',
    `status` = 0, `visible` = b'1', `keep_alive` = b'1', `always_show` = b'1', `update_time` = NOW()
WHERE `deleted` = b'0' AND `component` = 'finance/receipt/index';

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '银行到款查询', 'finance:receipt:query', 3, 1, page_menu.id, '', '', '', NULL,
       0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
FROM (SELECT `id` FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'finance/receipt/index' LIMIT 1) page_menu
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `permission` = 'finance:receipt:query'
);

UPDATE `system_menu`
SET `name` = '银行到款查询',
    `parent_id` = (SELECT page_menu.id FROM (SELECT `id` FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'finance/receipt/index' LIMIT 1) page_menu),
    `update_time` = NOW()
WHERE `deleted` = b'0' AND `permission` = 'finance:receipt:query';

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '银行到款导入', 'finance:receipt:import', 3, 2, page_menu.id, '', '', '', NULL,
       0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
FROM (SELECT `id` FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'finance/receipt/index' LIMIT 1) page_menu
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `permission` = 'finance:receipt:import'
);

UPDATE `system_menu`
SET `name` = '银行到款导入',
    `parent_id` = (SELECT page_menu.id FROM (SELECT `id` FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'finance/receipt/index' LIMIT 1) page_menu),
    `update_time` = NOW()
WHERE `deleted` = b'0' AND `permission` = 'finance:receipt:import';
