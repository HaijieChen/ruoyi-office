-- 开票申请台账 + 认领改挂字段（T1 / phase2a）
-- 幂等：CREATE IF NOT EXISTS + information_schema 判列后 PREPARE 加列；可重复执行
-- 参考：tech-plan-invoice-claim-r1 D-T5/D-T6；invoice-claim-redesign-decisions D2/D9

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. 开票申请主表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `finance_invoice_application` (
    `id`                        bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `application_no`            varchar(64) NOT NULL COMMENT '开票申请单号',
    `process_instance_id`       varchar(64) DEFAULT NULL COMMENT 'BPM 流程实例编号（最新）',
    `approval_status`           varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '审批状态（PENDING/APPROVED/REJECTED/CANCELLED）',
    `issue_status`              tinyint NOT NULL DEFAULT 0 COMMENT '办票状态（0-未开票，1-部分开票，2-全部开票）',
    `total_amount`              decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '价税合计（开票金额）',
    `confirmed_claimed_amount`  decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '已确认认领金额',
    `pending_claimed_amount`    decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '待确认认领占用金额',
    `applicant_user_id`         bigint NOT NULL COMMENT '申请人用户编号',
    `expected_invoice_date`     date DEFAULT NULL COMMENT '期望开票日期',
    `invoice_company`           varchar(128) DEFAULT NULL COMMENT '开票主体公司（表头快照，可与行一致）',
    `invoice_type`              varchar(32) DEFAULT NULL COMMENT '开票类型（普票/专票等，表头快照）',
    `buyer_name`                varchar(255) DEFAULT NULL COMMENT '购方名称（提交快照）',
    `buyer_tax_no`              varchar(64) DEFAULT NULL COMMENT '购方纳税人识别号（提交快照）',
    `buyer_address_phone`       varchar(512) DEFAULT NULL COMMENT '购方地址电话（提交快照）',
    `buyer_bank_account`        varchar(512) DEFAULT NULL COMMENT '购方开户行及账号（提交快照）',
    `special_invoice_requirement` varchar(500) DEFAULT NULL COMMENT '特别开票要求（提交快照）',
    `tax_content`               varchar(255) DEFAULT NULL COMMENT '开票内容/应税项目（提交快照）',
    `tax_rate`                  decimal(8,6) DEFAULT NULL COMMENT '税率/征收率（提交快照，如 0.060000）',
    `amount_excluding_tax`      decimal(18,2) DEFAULT NULL COMMENT '不含税金额（提交快照）',
    `tax_amount`                decimal(18,2) DEFAULT NULL COMMENT '税额（提交快照）',
    `evidence_file_url`         varchar(1024) DEFAULT NULL COMMENT '开票依据文件 URL（提交快照）',
    `remark`                    varchar(500) DEFAULT NULL COMMENT '特殊情况说明/备注',
    `voided`                    bit(1) NOT NULL DEFAULT b'0' COMMENT '是否作废',
    `creator`                   varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time`               datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`                   varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time`               datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                   bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`                 bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_application_no_tenant_deleted` (`application_no`, `tenant_id`, `deleted`),
    KEY `idx_process_instance_id` (`process_instance_id`),
    KEY `idx_approval_status` (`approval_status`),
    KEY `idx_applicant_user_id` (`applicant_user_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB COMMENT='财务开票申请台账';

-- ---------------------------------------------------------------------------
-- 2. 开票申请明细（一期：一行一票，票号/附件在行上）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `finance_invoice_application_line` (
    `id`                  bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `application_id`      bigint NOT NULL COMMENT '开票申请编号',
    `business_order_id`   bigint NOT NULL COMMENT '商务单编号',
    `amount`              decimal(18,2) NOT NULL COMMENT '本行开票金额',
    `invoice_company`     varchar(128) DEFAULT NULL COMMENT '开票主体公司（行快照）',
    `invoice_type`        varchar(32) DEFAULT NULL COMMENT '开票类型（行快照）',
    `billing_period`      varchar(16) DEFAULT NULL COMMENT '业务账期 YYYY-MM（行快照）',
    `issue_status`        tinyint NOT NULL DEFAULT 0 COMMENT '行办票状态（0-未开，1-已开）',
    `invoice_no`          varchar(128) DEFAULT NULL COMMENT '物理票号（一行一票）',
    `file_url`            varchar(1024) DEFAULT NULL COMMENT '发票附件 URL',
    `issued_at`           datetime DEFAULT NULL COMMENT '开票时间',
    `sort`                int NOT NULL DEFAULT 0 COMMENT '行序号',
    `creator`             varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time`         datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`             varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time`         datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`             bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`           bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_application_id` (`application_id`),
    KEY `idx_business_order_id` (`business_order_id`)
) ENGINE=InnoDB COMMENT='财务开票申请明细';

-- ---------------------------------------------------------------------------
-- 3. 商务单：开票占用金额
-- ---------------------------------------------------------------------------
SET @add_invoiced_occupied_amount = IF(
    EXISTS (
        SELECT 1 FROM `information_schema`.`columns`
        WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_business_order'
          AND `column_name` = 'invoiced_occupied_amount'
    ),
    'SELECT 1',
    'ALTER TABLE `finance_business_order` ADD COLUMN `invoiced_occupied_amount` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT ''开票占用金额（提交即占）'' AFTER `confirmed_claimed_amount`'
);
PREPARE phase2a_statement FROM @add_invoiced_occupied_amount;
EXECUTE phase2a_statement;
DEALLOCATE PREPARE phase2a_statement;

UPDATE `finance_business_order`
SET `invoiced_occupied_amount` = 0.00
WHERE `invoiced_occupied_amount` IS NULL;

ALTER TABLE `finance_business_order`
    MODIFY COLUMN `invoiced_occupied_amount` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '开票占用金额（提交即占）';

-- ---------------------------------------------------------------------------
-- 4. 到款：待确认认领占用
-- ---------------------------------------------------------------------------
SET @add_receipt_pending_claimed = IF(
    EXISTS (
        SELECT 1 FROM `information_schema`.`columns`
        WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_bank_receipt'
          AND `column_name` = 'pending_claimed_amount'
    ),
    'SELECT 1',
    'ALTER TABLE `finance_bank_receipt` ADD COLUMN `pending_claimed_amount` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT ''待确认认领占用金额'' AFTER `claimed_amount`'
);
PREPARE phase2a_statement FROM @add_receipt_pending_claimed;
EXECUTE phase2a_statement;
DEALLOCATE PREPARE phase2a_statement;

UPDATE `finance_bank_receipt`
SET `pending_claimed_amount` = 0.00
WHERE `pending_claimed_amount` IS NULL;

ALTER TABLE `finance_bank_receipt`
    MODIFY COLUMN `pending_claimed_amount` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '待确认认领占用金额';

-- ---------------------------------------------------------------------------
-- 5. 认领明细：改挂开票申请 + claim_source；business_order_id 可空（XOR 由应用层强制）
-- ---------------------------------------------------------------------------
SET @add_invoice_application_id = IF(
    EXISTS (
        SELECT 1 FROM `information_schema`.`columns`
        WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_receipt_claim_item'
          AND `column_name` = 'invoice_application_id'
    ),
    'SELECT 1',
    'ALTER TABLE `finance_receipt_claim_item` ADD COLUMN `invoice_application_id` bigint DEFAULT NULL COMMENT ''开票申请编号（INVOICE 源；与 business_order_id 应用层 XOR）'' AFTER `receipt_id`'
);
PREPARE phase2a_statement FROM @add_invoice_application_id;
EXECUTE phase2a_statement;
DEALLOCATE PREPARE phase2a_statement;

SET @add_claim_source = IF(
    EXISTS (
        SELECT 1 FROM `information_schema`.`columns`
        WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_receipt_claim_item'
          AND `column_name` = 'claim_source'
    ),
    'SELECT 1',
    'ALTER TABLE `finance_receipt_claim_item` ADD COLUMN `claim_source` varchar(32) DEFAULT NULL COMMENT ''认领来源（INVOICE/LEGACY_BO）'' AFTER `business_order_id`'
);
PREPARE phase2a_statement FROM @add_claim_source;
EXECUTE phase2a_statement;
DEALLOCATE PREPARE phase2a_statement;

-- 存量行：挂商务单 → LEGACY_BO；invoice_application_id 保持 NULL
UPDATE `finance_receipt_claim_item`
SET `claim_source` = 'LEGACY_BO'
WHERE `claim_source` IS NULL OR `claim_source` = '';

ALTER TABLE `finance_receipt_claim_item`
    MODIFY COLUMN `claim_source` varchar(32) NOT NULL DEFAULT 'LEGACY_BO' COMMENT '认领来源（INVOICE/LEGACY_BO）';

-- business_order_id 改为可空，支持新链路仅写 invoice_application_id
ALTER TABLE `finance_receipt_claim_item`
    MODIFY COLUMN `business_order_id` bigint DEFAULT NULL COMMENT '商务单编号（LEGACY_BO；与 invoice_application_id 应用层 XOR）';

SET @add_idx_invoice_application_id = IF(
    EXISTS (
        SELECT 1 FROM `information_schema`.`statistics`
        WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_receipt_claim_item'
          AND `index_name` = 'idx_invoice_application_id'
    ),
    'SELECT 1',
    'ALTER TABLE `finance_receipt_claim_item` ADD KEY `idx_invoice_application_id` (`invoice_application_id`)'
);
PREPARE phase2a_statement FROM @add_idx_invoice_application_id;
EXECUTE phase2a_statement;
DEALLOCATE PREPARE phase2a_statement;

SET @add_idx_claim_source = IF(
    EXISTS (
        SELECT 1 FROM `information_schema`.`statistics`
        WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_receipt_claim_item'
          AND `index_name` = 'idx_claim_source'
    ),
    'SELECT 1',
    'ALTER TABLE `finance_receipt_claim_item` ADD KEY `idx_claim_source` (`claim_source`)'
);
PREPARE phase2a_statement FROM @add_idx_claim_source;
EXECUTE phase2a_statement;
DEALLOCATE PREPARE phase2a_statement;
