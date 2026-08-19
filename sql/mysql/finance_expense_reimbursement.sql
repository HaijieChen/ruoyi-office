-- 费用报销台账 + 明细 + 事由分类字典（STORY-0011 U1）
-- 幂等：CREATE IF NOT EXISTS；字典按 type/value NOT EXISTS
-- 不建金额 helper。apply_amount 由服务按明细求和（U3）。

SET NAMES utf8mb4;

-- ----------------------------
-- Table: finance_expense_reimbursement
-- ----------------------------
CREATE TABLE IF NOT EXISTS `finance_expense_reimbursement` (
    `id`                         bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `process_title`              varchar(255) DEFAULT NULL COMMENT '流程标题【报销】-姓名-期间-总额',
    `period_label`               varchar(7)   NOT NULL COMMENT '费用归属期间 YYYY-MM',
    `payee_account_name`         varchar(128) NOT NULL COMMENT '收款户名（员工卡，手填）',
    `payee_account_no`           varchar(128) NOT NULL COMMENT '收款账号（员工卡，手填）',
    `apply_amount`               decimal(18,2) NOT NULL COMMENT '申请总额（明细合计）',
    `approved_amount`            decimal(18,2) DEFAULT NULL COMMENT '实报金额（财务审核，≤申请总额）',
    `company_bank_account_id`    bigint DEFAULT NULL COMMENT '出纳支付公司银行账户',
    `proxy_ticket`               bit(1) NOT NULL DEFAULT b'0' COMMENT '是否代票：1 仅代票明细，0 仅普通明细',
    `status`                     varchar(32)  NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING/WAIT_PAY/PAID/REJECTED/CANCELLED',
    `process_instance_id`        varchar(64)  DEFAULT NULL COMMENT '最新 BPM 实例',
    `applicant_user_id`          bigint NOT NULL COMMENT '申请人',
    `applicant_dept_id`          bigint DEFAULT NULL COMMENT '申请人部门',
    `apply_date`                 date DEFAULT NULL COMMENT '申请日',
    `finance_comment`            varchar(1000) DEFAULT NULL COMMENT '财务审核意见',
    `actual_pay_date`            date DEFAULT NULL COMMENT '实际支付日',
    `pay_voucher_url`            varchar(1024) DEFAULT NULL COMMENT '支付凭证',
    `creator`                    varchar(64)  DEFAULT '' COMMENT '创建者',
    `create_time`                datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`                    varchar(64)  DEFAULT '' COMMENT '更新者',
    `update_time`                datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                    bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`                  bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_fer_status` (`status`),
    KEY `idx_fer_applicant` (`applicant_user_id`),
    KEY `idx_fer_dept` (`applicant_dept_id`),
    KEY `idx_fer_process` (`process_instance_id`),
    KEY `idx_fer_period` (`period_label`),
    KEY `idx_fer_pay_account` (`company_bank_account_id`),
    KEY `idx_fer_create_time` (`create_time`)
) ENGINE=InnoDB COMMENT='费用报销申请';

-- ----------------------------
-- Table: finance_expense_reimbursement_line
-- line_kind 区分普通/代票；同一单只应保留与头 proxy_ticket 一致的行
-- ----------------------------
CREATE TABLE IF NOT EXISTS `finance_expense_reimbursement_line` (
    `id`                 bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `reimbursement_id`   bigint NOT NULL COMMENT '报销申请 id',
    `line_kind`          varchar(16)  NOT NULL COMMENT 'NORMAL 普通明细 / PROXY 代票明细',
    `category`           varchar(64)  NOT NULL COMMENT '普通=finance_expense_category；代票=手填费用类型',
    `fee_date`           date NOT NULL COMMENT '费用日期',
    `amount`             decimal(18,2) NOT NULL COMMENT '金额',
    `attachments`        varchar(2048) DEFAULT NULL COMMENT '附件 JSON URL 数组',
    `remark`             varchar(1000) DEFAULT NULL COMMENT '事由说明',
    `sort`               int NOT NULL DEFAULT 0 COMMENT '行序号',
    `creator`            varchar(64)  DEFAULT '' COMMENT '创建者',
    `create_time`        datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`            varchar(64)  DEFAULT '' COMMENT '更新者',
    `update_time`        datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`            bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`          bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_ferl_reimbursement` (`reimbursement_id`),
    KEY `idx_ferl_kind` (`line_kind`)
) ENGINE=InnoDB COMMENT='费用报销明细（普通/代票）';

-- ----------------------------
-- Dict: finance_expense_category
-- ----------------------------
INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '费用报销事由分类', 'finance_expense_category', 0, '普通报销明细事由分类', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_type` WHERE `type` = 'finance_expense_category' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT d.sort, d.label, d.value, 'finance_expense_category', 0, d.color_type, '', d.remark, 'admin', NOW(), 'admin', NOW(), b'0'
FROM (
    SELECT 1 AS sort, '差旅' AS label, 'travel' AS value, 'primary' AS color_type, '差旅费' AS remark
    UNION ALL SELECT 2, '招待', 'entertain', 'warning', '招待费'
    UNION ALL SELECT 3, '办公', 'office', 'success', '办公费'
    UNION ALL SELECT 4, '交通', 'transport', 'primary', '交通费'
    UNION ALL SELECT 5, '通讯', 'comms', '', '通讯费'
    UNION ALL SELECT 6, '采购', 'purchase', 'info', '采购'
    UNION ALL SELECT 7, '其他', 'other', 'info', '其他'
) d
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` x
    WHERE x.`dict_type` = 'finance_expense_category' AND x.`value` = d.value AND x.`deleted` = b'0'
);
