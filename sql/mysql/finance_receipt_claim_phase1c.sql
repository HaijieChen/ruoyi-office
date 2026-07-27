SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `finance_receipt_claim` (
    `id`                 bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `claimant_id`        bigint NOT NULL COMMENT '认领人编号',
    `status`             tinyint NOT NULL DEFAULT 0 COMMENT '状态（0-待确认，1-已确认，2-已驳回）',
    `total_claim_amount` decimal(18,2) NOT NULL COMMENT '认领总金额',
    `remark`             varchar(500) DEFAULT NULL COMMENT '说明',
    `reject_reason`      varchar(500) DEFAULT NULL COMMENT '驳回原因',
    `reviewer_id`        bigint DEFAULT NULL COMMENT '复核人编号',
    `review_time`        datetime DEFAULT NULL COMMENT '复核时间',
    `creator`            varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time`        datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`            varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time`        datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`            bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`          bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_claimant_status` (`claimant_id`, `status`),
    KEY `idx_status_create_time` (`status`, `create_time`)
) ENGINE=InnoDB COMMENT='财务到款认领单';

CREATE TABLE IF NOT EXISTS `finance_receipt_claim_item` (
    `id`                bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `claim_id`          bigint NOT NULL COMMENT '认领单编号',
    `receipt_id`        bigint NOT NULL COMMENT '银行到款编号',
    `business_order_id` bigint NOT NULL COMMENT '商务单编号',
    `claim_amount`      decimal(18,2) NOT NULL COMMENT '认领金额',
    `creator`           varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time`       datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`           varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time`       datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`         bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_claim_id` (`claim_id`),
    KEY `idx_receipt_id` (`receipt_id`),
    KEY `idx_business_order_id` (`business_order_id`)
) ENGINE=InnoDB COMMENT='财务到款认领明细';

SET @add_reject_reason = IF(
    EXISTS (
        SELECT 1 FROM `information_schema`.`columns`
        WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_receipt_claim'
          AND `column_name` = 'reject_reason'
    ),
    'SELECT 1',
    'ALTER TABLE `finance_receipt_claim` ADD COLUMN `reject_reason` varchar(500) DEFAULT NULL COMMENT ''驳回原因'' AFTER `remark`'
);
PREPARE phase1c_statement FROM @add_reject_reason;
EXECUTE phase1c_statement;
DEALLOCATE PREPARE phase1c_statement;

SET @add_reviewer_id = IF(
    EXISTS (
        SELECT 1 FROM `information_schema`.`columns`
        WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_receipt_claim'
          AND `column_name` = 'reviewer_id'
    ),
    'SELECT 1',
    'ALTER TABLE `finance_receipt_claim` ADD COLUMN `reviewer_id` bigint DEFAULT NULL COMMENT ''复核人编号'' AFTER `reject_reason`'
);
PREPARE phase1c_statement FROM @add_reviewer_id;
EXECUTE phase1c_statement;
DEALLOCATE PREPARE phase1c_statement;

SET @add_review_time = IF(
    EXISTS (
        SELECT 1 FROM `information_schema`.`columns`
        WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_receipt_claim'
          AND `column_name` = 'review_time'
    ),
    'SELECT 1',
    'ALTER TABLE `finance_receipt_claim` ADD COLUMN `review_time` datetime DEFAULT NULL COMMENT ''复核时间'' AFTER `reviewer_id`'
);
PREPARE phase1c_statement FROM @add_review_time;
EXECUTE phase1c_statement;
DEALLOCATE PREPARE phase1c_statement;

SET @add_confirmed_claimed_amount = IF(
    EXISTS (
        SELECT 1 FROM `information_schema`.`columns`
        WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_business_order'
          AND `column_name` = 'confirmed_claimed_amount'
    ),
    'SELECT 1',
    'ALTER TABLE `finance_business_order` ADD COLUMN `confirmed_claimed_amount` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT ''已确认认领金额'' AFTER `receivable_amount`'
);
PREPARE phase1c_statement FROM @add_confirmed_claimed_amount;
EXECUTE phase1c_statement;
DEALLOCATE PREPARE phase1c_statement;

UPDATE `finance_business_order`
SET `confirmed_claimed_amount` = 0.00
WHERE `confirmed_claimed_amount` IS NULL;

ALTER TABLE `finance_business_order`
    MODIFY COLUMN `confirmed_claimed_amount` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '已确认认领金额';

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '到款认领', '', 2, 3, parent_menu.id, 'receipt-claim', 'fa:handshake', 'finance/receipt-claim/index', 'FinanceReceiptClaimMyPage',
       0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
FROM (SELECT `id` FROM `system_menu` WHERE `deleted` = b'0' AND `type` = 1 AND `parent_id` = 0 AND `path` IN ('finance', '/finance') LIMIT 1) parent_menu
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'finance/receipt-claim/index'
);

UPDATE `system_menu`
SET `name` = '到款认领',
    `parent_id` = (SELECT parent_menu.id FROM (SELECT `id` FROM `system_menu` WHERE `deleted` = b'0' AND `type` = 1 AND `parent_id` = 0 AND `path` IN ('finance', '/finance') LIMIT 1) parent_menu),
    `path` = 'receipt-claim', `sort` = 3, `icon` = 'fa:handshake', `component` = 'finance/receipt-claim/index', `component_name` = 'FinanceReceiptClaimMyPage',
    `status` = 0, `visible` = b'1', `keep_alive` = b'1', `always_show` = b'1', `update_time` = NOW()
WHERE `deleted` = b'0' AND `component` = 'finance/receipt-claim/index';

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT permission_menu.name, permission_menu.permission, 3, permission_menu.sort, page_menu.id, '', '', '', NULL,
       0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
FROM (SELECT `id` FROM `system_menu`
      WHERE `deleted` = b'0' AND `component` = 'finance/receipt-claim/index' LIMIT 1) page_menu
JOIN (
    SELECT '到款认领查询' AS name, 'finance:receipt-claim:query' AS permission, 10 AS sort
    UNION ALL SELECT '到款认领创建', 'finance:receipt-claim:create', 11
    UNION ALL SELECT '到款认领更新', 'finance:receipt-claim:update', 12
    UNION ALL SELECT '到款认领重提', 'finance:receipt-claim:resubmit', 13
    UNION ALL SELECT '到款认领复核', 'finance:receipt-claim:review', 14
    UNION ALL SELECT '到款认领确认', 'finance:receipt-claim:confirm', 15
    UNION ALL SELECT '到款认领驳回', 'finance:receipt-claim:reject', 16
) permission_menu
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `permission` = permission_menu.permission
);

UPDATE `system_menu`
JOIN (
    SELECT '到款认领查询' AS name, 'finance:receipt-claim:query' AS permission, 10 AS sort
    UNION ALL SELECT '到款认领创建', 'finance:receipt-claim:create', 11
    UNION ALL SELECT '到款认领更新', 'finance:receipt-claim:update', 12
    UNION ALL SELECT '到款认领重提', 'finance:receipt-claim:resubmit', 13
    UNION ALL SELECT '到款认领复核', 'finance:receipt-claim:review', 14
    UNION ALL SELECT '到款认领确认', 'finance:receipt-claim:confirm', 15
    UNION ALL SELECT '到款认领驳回', 'finance:receipt-claim:reject', 16
) permission_menu ON `system_menu`.`permission` = permission_menu.permission
SET `system_menu`.`name` = permission_menu.name,
    `system_menu`.`sort` = permission_menu.sort,
    `system_menu`.`parent_id` = (
        SELECT page_menu.id FROM (
            SELECT `id` FROM `system_menu`
            WHERE `deleted` = b'0' AND `component` = 'finance/receipt-claim/index' LIMIT 1
        ) page_menu
    ),
    `system_menu`.`update_time` = NOW()
WHERE `system_menu`.`deleted` = b'0';
