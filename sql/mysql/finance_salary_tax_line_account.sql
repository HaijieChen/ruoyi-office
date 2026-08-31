-- 薪资/税金付款明细行增加公司银行账户及快照；发起人可拉账户 simple-list。

SET NAMES utf8mb4;

ALTER TABLE `finance_payment_salary_line`
    ADD COLUMN `company_bank_account_id` bigint DEFAULT NULL COMMENT '公司银行账户 id' AFTER `entity_company_name`,
    ADD COLUMN `account_name_snapshot` varchar(128) DEFAULT NULL COMMENT '账户名称快照' AFTER `company_bank_account_id`,
    ADD COLUMN `bank_name_snapshot` varchar(128) DEFAULT NULL COMMENT '开户行快照' AFTER `account_name_snapshot`,
    ADD COLUMN `account_no_masked_snapshot` varchar(64) DEFAULT NULL COMMENT '脱敏账号快照' AFTER `bank_name_snapshot`;

ALTER TABLE `finance_payment_tax_line`
    ADD COLUMN `company_bank_account_id` bigint DEFAULT NULL COMMENT '公司银行账户 id' AFTER `entity_company_name`,
    ADD COLUMN `account_name_snapshot` varchar(128) DEFAULT NULL COMMENT '账户名称快照' AFTER `company_bank_account_id`,
    ADD COLUMN `bank_name_snapshot` varchar(128) DEFAULT NULL COMMENT '开户行快照' AFTER `account_name_snapshot`,
    ADD COLUMN `account_no_masked_snapshot` varchar(64) DEFAULT NULL COMMENT '脱敏账号快照' AFTER `bank_name_snapshot`;

-- 发起薪资/税金付款的角色需要能按公司拉启用账户
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0' AND r.`tenant_id` = 1
  AND r.`code` IN ('business_staff', 'finance_admin')
  AND m.`deleted` = b'0'
  AND m.`permission` = 'finance:company-bank-account:simple-list'
  AND NOT EXISTS (
      SELECT 1 FROM `system_role_menu` rm
      WHERE rm.`role_id` = r.`id` AND rm.`menu_id` = m.`id` AND rm.`tenant_id` = 1 AND rm.`deleted` = b'0'
  );
