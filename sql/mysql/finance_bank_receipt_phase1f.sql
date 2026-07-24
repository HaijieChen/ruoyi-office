CREATE TABLE IF NOT EXISTS `finance_receipt_lifecycle_audit` (
    `id`          bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `receipt_id`  bigint NOT NULL COMMENT '银行到款编号',
    `action`      tinyint NOT NULL COMMENT '操作（1-关闭，2-重开）',
    `operator_id` bigint NOT NULL COMMENT '操作人编号',
    `action_time` datetime NOT NULL COMMENT '操作时间',
    `reason`      varchar(500) NOT NULL COMMENT '操作原因',
    `creator`     varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`   bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_receipt_id_action_time` (`receipt_id`, `action_time`)
) ENGINE=InnoDB COMMENT='财务银行到款生命周期审计';

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '银行到款关闭', 'finance:receipt:close', 3, 3, page_menu.id, '', '', '', NULL,
       0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
FROM (SELECT `id` FROM `system_menu`
      WHERE `deleted` = b'0' AND `component` = 'finance/receipt/index' LIMIT 1) page_menu
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `permission` = 'finance:receipt:close'
);

UPDATE `system_menu`
SET `name` = '银行到款关闭', `sort` = 3,
    `parent_id` = (SELECT page_menu.id FROM (
        SELECT `id` FROM `system_menu`
        WHERE `deleted` = b'0' AND `component` = 'finance/receipt/index' LIMIT 1
    ) page_menu),
    `update_time` = NOW()
WHERE `deleted` = b'0' AND `permission` = 'finance:receipt:close';

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '银行到款重开', 'finance:receipt:reopen', 3, 4, page_menu.id, '', '', '', NULL,
       0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
FROM (SELECT `id` FROM `system_menu`
      WHERE `deleted` = b'0' AND `component` = 'finance/receipt/index' LIMIT 1) page_menu
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `permission` = 'finance:receipt:reopen'
);

UPDATE `system_menu`
SET `name` = '银行到款重开', `sort` = 4,
    `parent_id` = (SELECT page_menu.id FROM (
        SELECT `id` FROM `system_menu`
        WHERE `deleted` = b'0' AND `component` = 'finance/receipt/index' LIMIT 1
    ) page_menu),
    `update_time` = NOW()
WHERE `deleted` = b'0' AND `permission` = 'finance:receipt:reopen';

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '银行到款操作审计查询', 'finance:receipt:audit-query', 3, 5, page_menu.id, '', '', '', NULL,
       0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
FROM (SELECT `id` FROM `system_menu`
      WHERE `deleted` = b'0' AND `component` = 'finance/receipt/index' LIMIT 1) page_menu
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `permission` = 'finance:receipt:audit-query'
);

UPDATE `system_menu`
SET `name` = '银行到款操作审计查询', `sort` = 5,
    `parent_id` = (SELECT page_menu.id FROM (
        SELECT `id` FROM `system_menu`
        WHERE `deleted` = b'0' AND `component` = 'finance/receipt/index' LIMIT 1
    ) page_menu),
    `update_time` = NOW()
WHERE `deleted` = b'0' AND `permission` = 'finance:receipt:audit-query';
