-- Mirror of repository root sql/mysql/finance_contract_application_phase1.sql (CS-T1)
-- 合同签约申请台账 + 商务单正式关联列

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `finance_contract_application` (
    `id`                          bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `application_no`              varchar(64) NOT NULL COMMENT '合同签约申请业务单号',
    `process_instance_id`         varchar(64) DEFAULT NULL COMMENT 'BPM 流程实例编号（最新）',
    `approval_status`             varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '审批状态（PENDING/APPROVED/REJECTED/CANCELLED）',
    `current_node_key`            varchar(64) DEFAULT NULL COMMENT '当前节点 key（冗余，列表用）',
    `current_node_name`           varchar(128) DEFAULT NULL COMMENT '当前节点显示名',
    `applicant_user_id`           bigint NOT NULL COMMENT '申请人用户编号',
    `applicant_dept_id`           bigint DEFAULT NULL COMMENT '申请人部门编号（快照可选）',
    `counterparty_company_id`     bigint DEFAULT NULL COMMENT '对方客商公司编号（finance_customer_company）',
    `counterparty_name`           varchar(255) DEFAULT NULL COMMENT '对方名称（提交快照）',
    `amount_na`                   bit(1) NOT NULL DEFAULT b'0' COMMENT '金额不适用（1 时 contract_amount 可空）',
    `contract_amount`             decimal(18,2) DEFAULT NULL COMMENT '合同金额',
    `sign_company`                varchar(128) DEFAULT NULL COMMENT '签约主体公司',
    `file_name`                   varchar(255) DEFAULT NULL COMMENT '用印文件名称',
    `file_type`                   varchar(64) DEFAULT NULL COMMENT '文件类型/事由',
    `product_type`                varchar(64) DEFAULT NULL COMMENT '产品类型',
    `rebate_ratio`                varchar(128) DEFAULT NULL COMMENT '返点比例（文本）',
    `settlement_method`           varchar(64) DEFAULT NULL COMMENT '结算方式',
    `copy_count`                  int DEFAULT NULL COMMENT '文件份数',
    `seal_types`                  varchar(512) DEFAULT NULL COMMENT '需加盖印章类型（JSON 数组或逗号分隔）',
    `need_mail`                   bit(1) NOT NULL DEFAULT b'0' COMMENT '是否邮寄与交付',
    `mail_address`                varchar(512) DEFAULT NULL COMMENT '邮寄地址（need_mail=1 时）',
    `pre_process_ref`             varchar(255) DEFAULT NULL COMMENT '前置流程引用（采购/租赁等条件必填）',
    `start_date`                  date DEFAULT NULL COMMENT '合同起始日期',
    `end_date`                    date DEFAULT NULL COMMENT '合同结束日期（将到期筛选）',
    `draft_file_url`              varchar(1024) DEFAULT NULL COMMENT '用印文件电子版 URL（提交）',
    `seal_file_url`               varchar(1024) DEFAULT NULL COMMENT '用印备案扫描件 URL（用印节点）',
    `actual_sealer_user_id`       bigint DEFAULT NULL COMMENT '实际用印人',
    `archived_at`                 datetime DEFAULT NULL COMMENT '归档完成时间',
    `mail_tracking_no`            varchar(128) DEFAULT NULL COMMENT '邮寄单号',
    `remark`                      varchar(500) DEFAULT NULL COMMENT '备注',
    `voided`                      bit(1) NOT NULL DEFAULT b'0' COMMENT '是否作废',
    `creator`                     varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time`                 datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`                     varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time`                 datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                     bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`                   bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_contract_app_no_tenant_deleted` (`application_no`, `tenant_id`, `deleted`),
    KEY `idx_process_instance_id` (`process_instance_id`),
    KEY `idx_approval_status` (`approval_status`),
    KEY `idx_applicant_user_id` (`applicant_user_id`),
    KEY `idx_counterparty_company_id` (`counterparty_company_id`),
    KEY `idx_end_date` (`end_date`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB COMMENT='财务合同签约申请台账';

SET @add_contract_application_id = IF(
    EXISTS (
        SELECT 1 FROM `information_schema`.`columns`
        WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_business_order'
          AND `column_name` = 'contract_application_id'
    ),
    'SELECT 1',
    'ALTER TABLE `finance_business_order` ADD COLUMN `contract_application_id` bigint DEFAULT NULL COMMENT ''合同签约申请编号（正式关联）'' AFTER `contract_process_id`'
);
PREPARE cs_t1_statement FROM @add_contract_application_id;
EXECUTE cs_t1_statement;
DEALLOCATE PREPARE cs_t1_statement;

SET @add_idx_contract_application_id = IF(
    EXISTS (
        SELECT 1 FROM `information_schema`.`statistics`
        WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_business_order'
          AND `index_name` = 'idx_contract_application_id'
    ),
    'SELECT 1',
    'ALTER TABLE `finance_business_order` ADD KEY `idx_contract_application_id` (`contract_application_id`)'
);
PREPARE cs_t1_statement FROM @add_idx_contract_application_id;
EXECUTE cs_t1_statement;
DEALLOCATE PREPARE cs_t1_statement;
