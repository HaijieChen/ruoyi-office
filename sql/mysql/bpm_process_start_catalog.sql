-- 流程发起目录分类（假勤/财务/法务/行政/人事）并回填已有模型。幂等。
SET NAMES utf8mb4;

INSERT INTO `bpm_category` (`name`, `code`, `description`, `status`, `sort`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT '假勤', 'attendance', '请假出差外出加班补卡', 0, 10, 'admin', NOW(), 'admin', NOW(), b'0', 1
WHERE NOT EXISTS (SELECT 1 FROM `bpm_category` WHERE `deleted` = b'0' AND `code` = 'attendance');

INSERT INTO `bpm_category` (`name`, `code`, `description`, `status`, `sort`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT '财务', 'finance', '发票报销付款采购', 0, 20, 'admin', NOW(), 'admin', NOW(), b'0', 1
WHERE NOT EXISTS (SELECT 1 FROM `bpm_category` WHERE `deleted` = b'0' AND `code` = 'finance');

INSERT INTO `bpm_category` (`name`, `code`, `description`, `status`, `sort`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT '法务', 'legal', '用印证照合同', 0, 30, 'admin', NOW(), 'admin', NOW(), b'0', 1
WHERE NOT EXISTS (SELECT 1 FROM `bpm_category` WHERE `deleted` = b'0' AND `code` = 'legal');

INSERT INTO `bpm_category` (`name`, `code`, `description`, `status`, `sort`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT '行政', 'admin', '报废入库领用来访通用审批', 0, 40, 'admin', NOW(), 'admin', NOW(), b'0', 1
WHERE NOT EXISTS (SELECT 1 FROM `bpm_category` WHERE `deleted` = b'0' AND `code` = 'admin');

INSERT INTO `bpm_category` (`name`, `code`, `description`, `status`, `sort`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT '人事', 'hrm', '入职转正调动离职绩效', 0, 50, 'admin', NOW(), 'admin', NOW(), b'0', 1
WHERE NOT EXISTS (SELECT 1 FROM `bpm_category` WHERE `deleted` = b'0' AND `code` = 'hrm');

UPDATE `bpm_process_definition_info` i
INNER JOIN `ACT_RE_PROCDEF` p ON p.`ID_` = i.`process_definition_id`
SET i.`category` = CASE p.`KEY_`
    WHEN 'oa_business_trip' THEN 'attendance'
    WHEN 'oa_outing' THEN 'attendance'
    WHEN 'oa_leave' THEN 'attendance'
    WHEN 'finance_invoice_apply' THEN 'finance'
    WHEN 'finance_invoice_redflush_apply' THEN 'finance'
    WHEN 'oa_expense_reimbursement' THEN 'finance'
    WHEN 'oa_expense_no_invoice' THEN 'finance'
    WHEN 'finance_payment_apply' THEN 'finance'
    WHEN 'oa_purchase_apply' THEN 'finance'
    WHEN 'finance_tax_payment_apply' THEN 'finance'
    WHEN 'finance_salary_payment_apply' THEN 'finance'
    WHEN 'finance_contract_sign' THEN 'legal'
    ELSE i.`category`
END
WHERE i.`deleted` = b'0';
