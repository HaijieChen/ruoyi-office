SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `finance_business_order` (
    `id`                bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `order_no`          varchar(64) NOT NULL COMMENT '商务单号',
    `business_subject`  varchar(255) NOT NULL COMMENT '业务主体/客户',
    `business_type`     varchar(50) NOT NULL COMMENT '业务类型',
    `contract_ref`      varchar(127) DEFAULT NULL COMMENT '合同引用',
    `project_ref`       varchar(127) DEFAULT NULL COMMENT '项目引用',
    `receivable_amount` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '应收金额',
    `payable_amount`    decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '应付金额',
    `currency`          varchar(3) NOT NULL DEFAULT 'CNY' COMMENT '币种',
    `owner_id`          bigint NOT NULL COMMENT '负责人编号',
    `status`            tinyint NOT NULL DEFAULT 0 COMMENT '状态（0-草稿，1-有效，2-关闭）',
    `remark`            varchar(500) DEFAULT NULL COMMENT '备注',
    `creator`           varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time`       datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`           varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time`       datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`         bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no_tenant_deleted` (`order_no`, `tenant_id`, `deleted`),
    KEY `idx_business_subject` (`business_subject`),
    KEY `idx_business_type` (`business_type`),
    KEY `idx_status` (`status`),
    KEY `idx_owner_id` (`owner_id`)
) ENGINE=InnoDB COMMENT='财务商务单';

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '财务管理', '', 1, 40, 0, 'finance', 'fa:money', NULL, NULL,
       0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `type` = 1 AND `parent_id` = 0 AND `path` = 'finance'
);

UPDATE `system_menu`
SET `name` = '财务管理', `sort` = 40, `parent_id` = 0, `icon` = 'fa:money', `component` = NULL, `component_name` = NULL,
    `status` = 0, `visible` = b'1', `keep_alive` = b'1', `always_show` = b'1', `update_time` = NOW()
WHERE `deleted` = b'0' AND `type` = 1 AND `parent_id` = 0 AND `path` = 'finance';

UPDATE `system_menu`
SET `name` = 'ERP 财务管理', `update_time` = NOW()
WHERE `deleted` = b'0' AND `path` = 'finance' AND `parent_id` <> 0;

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '商务单', '', 2, 2, parent_menu.id, 'business-order', 'fa:briefcase', 'finance/business-order/index', 'FinanceBusinessOrder',
       0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
FROM (SELECT `id` FROM `system_menu` WHERE `deleted` = b'0' AND `type` = 1 AND `parent_id` = 0 AND `path` = 'finance' LIMIT 1) parent_menu
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'finance/business-order/index'
);

UPDATE `system_menu`
SET `name` = '商务单',
    `parent_id` = (SELECT parent_menu.id FROM (SELECT `id` FROM `system_menu` WHERE `deleted` = b'0' AND `type` = 1 AND `parent_id` = 0 AND `path` = 'finance' LIMIT 1) parent_menu),
    `path` = 'business-order', `sort` = 2, `icon` = 'fa:briefcase', `component` = 'finance/business-order/index', `component_name` = 'FinanceBusinessOrder',
    `status` = 0, `visible` = b'1', `keep_alive` = b'1', `always_show` = b'1', `update_time` = NOW()
WHERE `deleted` = b'0' AND `component` = 'finance/business-order/index';

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT permission_menu.name, permission_menu.permission, 3, permission_menu.sort, page_menu.id, '', '', '', NULL,
       0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
FROM (SELECT `id` FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'finance/business-order/index' LIMIT 1) page_menu
JOIN (
    SELECT '商务单查询' AS name, 'finance:business-order:query' AS permission, 1 AS sort
    UNION ALL SELECT '商务单创建', 'finance:business-order:create', 2
    UNION ALL SELECT '商务单更新', 'finance:business-order:update', 3
    UNION ALL SELECT '商务单删除', 'finance:business-order:delete', 4
) permission_menu
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `permission` = permission_menu.permission
);

UPDATE `system_menu`
JOIN (
    SELECT '商务单查询' AS name, 'finance:business-order:query' AS permission, 1 AS sort
    UNION ALL SELECT '商务单创建', 'finance:business-order:create', 2
    UNION ALL SELECT '商务单更新', 'finance:business-order:update', 3
    UNION ALL SELECT '商务单删除', 'finance:business-order:delete', 4
) permission_menu ON `system_menu`.`permission` = permission_menu.permission
SET `system_menu`.`name` = permission_menu.name,
    `system_menu`.`sort` = permission_menu.sort,
    `system_menu`.`parent_id` = (SELECT page_menu.id FROM (SELECT `id` FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'finance/business-order/index' LIMIT 1) page_menu),
    `system_menu`.`update_time` = NOW()
WHERE `system_menu`.`deleted` = b'0';
