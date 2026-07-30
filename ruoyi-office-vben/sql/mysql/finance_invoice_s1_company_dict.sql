-- S1：开票公司组织 deptId + 发票类型字典 finance_invoice_type（专票/普票）
-- 幂等可重复执行

SET NAMES utf8mb4;

-- 1) 开票申请主表：组织公司部门编号
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'finance_invoice_application'
      AND COLUMN_NAME = 'invoice_company_dept_id'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `finance_invoice_application` ADD COLUMN `invoice_company_dept_id` bigint DEFAULT NULL COMMENT ''开票公司组织部门编号（orgType=公司）'' AFTER `invoice_company`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 字典类型
INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '财务发票类型', 'finance_invoice_type', 0, '开票申请 invoiceType：专票/普票', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_type` WHERE `type` = 'finance_invoice_type' AND `deleted` = b'0'
);

-- 3) 字典数据（中文 value，与历史数据兼容）
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 1, '专票', '专票', 'finance_invoice_type', 0, 'primary', '', '增值税专用发票', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'finance_invoice_type' AND `value` = '专票' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 2, '普票', '普票', 'finance_invoice_type', 0, 'default', '', '增值税普通发票', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'finance_invoice_type' AND `value` = '普票' AND `deleted` = b'0'
);
