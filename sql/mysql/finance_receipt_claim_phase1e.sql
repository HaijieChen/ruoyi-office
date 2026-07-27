-- OA-18: 后端撤销已确认认领单
-- 幂等迁移：撤销原因字段 + 审计表 + 撤销权限

-- 1. 添加 revoke_reason 列
SET @add_revoke_reason = IF(
    EXISTS (
        SELECT 1 FROM `information_schema`.`columns`
        WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_receipt_claim'
          AND `column_name` = 'revoke_reason'
    ),
    'SELECT 1',
    'ALTER TABLE `finance_receipt_claim` ADD COLUMN `revoke_reason` varchar(500) DEFAULT NULL COMMENT ''撤销原因'' AFTER `reject_reason`'
);
PREPARE phase1e_statement FROM @add_revoke_reason;
EXECUTE phase1e_statement;
DEALLOCATE PREPARE phase1e_statement;

-- 2. 创建撤销审计表
CREATE TABLE IF NOT EXISTS `finance_receipt_claim_revoke_audit` (
    `id`            bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `claim_id`      bigint NOT NULL COMMENT '认领单编号',
    `reviewer_id`   bigint NOT NULL COMMENT '撤销操作人编号',
    `revoke_time`   datetime NOT NULL COMMENT '撤销时间',
    `revoke_reason` varchar(500) NOT NULL COMMENT '撤销原因',
    `creator`       varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`       varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`     bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_claim_id` (`claim_id`)
) ENGINE=InnoDB COMMENT='财务到款认领撤销审计';

-- 3. 注册撤销权限按钮
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '到款认领撤销', 'finance:receipt-claim:revoke', 3, 17, page_menu.id, '', '', '', NULL,
       0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
FROM (SELECT `id` FROM `system_menu`
      WHERE `deleted` = b'0' AND `component` = 'finance/receipt-claim/index' LIMIT 1) page_menu
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `permission` = 'finance:receipt-claim:revoke'
);

UPDATE `system_menu`
SET `name` = '到款认领撤销',
    `sort` = 17,
    `parent_id` = (
        SELECT page_menu.id FROM (
            SELECT `id` FROM `system_menu`
            WHERE `deleted` = b'0' AND `component` = 'finance/receipt-claim/index' LIMIT 1
        ) page_menu
    ),
    `update_time` = NOW()
WHERE `deleted` = b'0' AND `permission` = 'finance:receipt-claim:revoke';

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '到款认领复核', '', 2, 4, finance_menu.id, 'receipt-claim-review', 'fa:check-double',
       'finance/receipt-claim/review', 'FinanceReceiptClaimReviewPage',
       0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
FROM (SELECT `id` FROM `system_menu`
      WHERE `deleted` = b'0' AND `type` = 1 AND `parent_id` = 0 AND `path` IN ('finance', '/finance') LIMIT 1) finance_menu
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'finance/receipt-claim/review'
);

UPDATE `system_menu`
SET `name` = '到款认领复核',
    `parent_id` = (
        SELECT finance_menu.id FROM (
            SELECT `id` FROM `system_menu`
            WHERE `deleted` = b'0' AND `type` = 1 AND `parent_id` = 0 AND `path` IN ('finance', '/finance') LIMIT 1
        ) finance_menu
    ),
    `path` = 'receipt-claim-review',
    `sort` = 4,
    `icon` = 'fa:check-double',
    `component_name` = 'FinanceReceiptClaimReviewPage',
    `status` = 0,
    `visible` = b'1',
    `keep_alive` = b'1',
    `always_show` = b'1',
    `update_time` = NOW()
WHERE `deleted` = b'0' AND `component` = 'finance/receipt-claim/review';
