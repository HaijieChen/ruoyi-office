-- 2026-08-20 meeting: expense dual category, actual user, payment materials, fx rate
SET NAMES utf8mb4;

SET @sql := (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='finance_expense_reimbursement' AND COLUMN_NAME='actual_user_id'), 'SELECT 1',
  'ALTER TABLE finance_expense_reimbursement ADD COLUMN actual_user_id BIGINT NULL COMMENT "actual reimbursed user" AFTER applicant_user_id'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='finance_expense_reimbursement' AND COLUMN_NAME='entity_company_name'), 'SELECT 1',
  'ALTER TABLE finance_expense_reimbursement ADD COLUMN entity_company_name VARCHAR(128) NULL AFTER applicant_dept_id'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='finance_expense_reimbursement' AND COLUMN_NAME='entity_company_dept_id'), 'SELECT 1',
  'ALTER TABLE finance_expense_reimbursement ADD COLUMN entity_company_dept_id BIGINT NULL AFTER entity_company_name'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
UPDATE finance_expense_reimbursement SET actual_user_id = applicant_user_id WHERE actual_user_id IS NULL AND deleted = b'0';

SET @sql := (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='finance_expense_reimbursement_line' AND COLUMN_NAME='invoice_type'), 'SELECT 1',
  'ALTER TABLE finance_expense_reimbursement_line ADD COLUMN invoice_type VARCHAR(64) NULL AFTER category'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='finance_expense_reimbursement_line' AND COLUMN_NAME='sub_item'), 'SELECT 1',
  'ALTER TABLE finance_expense_reimbursement_line ADD COLUMN sub_item VARCHAR(64) NULL AFTER invoice_type'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='finance_payment_application' AND COLUMN_NAME='materials_status'), 'SELECT 1',
  'ALTER TABLE finance_payment_application ADD COLUMN materials_status VARCHAR(32) NOT NULL DEFAULT "COMPLETE" AFTER status'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


CREATE TABLE IF NOT EXISTS finance_exchange_rate (
  id bigint NOT NULL AUTO_INCREMENT,
  period_label varchar(7) NOT NULL,
  from_currency varchar(16) NOT NULL DEFAULT 'USD',
  to_currency varchar(16) NOT NULL DEFAULT 'CNY',
  rate decimal(18,8) NOT NULL,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_fx_period_ccy (period_label, from_currency, to_currency, tenant_id, deleted)
) ENGINE=InnoDB COMMENT='monthly fx rate';

INSERT INTO system_dict_type (name, type, status, remark, creator, create_time, updater, update_time, deleted)
SELECT '发票类型', 'finance_invoice_type', 0, '报销抵票发票类别', 'admin', NOW(), 'admin', NOW(), b'0' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM system_dict_type WHERE type='finance_invoice_type' AND deleted=b'0');
INSERT INTO system_dict_type (name, type, status, remark, creator, create_time, updater, update_time, deleted)
SELECT '费用子项目', 'finance_expense_subitem', 0, 'remark=parent category', 'admin', NOW(), 'admin', NOW(), b'0' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM system_dict_type WHERE type='finance_expense_subitem' AND deleted=b'0');

INSERT INTO system_dict_data (sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted)
SELECT d.sort, d.label, d.value, d.dtype, 0, '', '', d.remark, 'admin', NOW(), 'admin', NOW(), b'0'
FROM (
  SELECT 1 sort, '餐饮票' label, 'meal' value, 'finance_invoice_type' dtype, '' remark
  UNION ALL SELECT 2, '交通票', 'transport', 'finance_invoice_type', ''
  UNION ALL SELECT 3, '住宿票', 'hotel', 'finance_invoice_type', ''
  UNION ALL SELECT 4, '办公票', 'office', 'finance_invoice_type', ''
  UNION ALL SELECT 5, '服务票', 'service', 'finance_invoice_type', ''
  UNION ALL SELECT 6, '其他发票', 'other', 'finance_invoice_type', ''
  UNION ALL SELECT 1, '交通', 'entertain.transport', 'finance_expense_subitem', 'entertain'
  UNION ALL SELECT 2, '住宿', 'entertain.stay', 'finance_expense_subitem', 'entertain'
  UNION ALL SELECT 3, '娱乐活动', 'entertain.entertainment', 'finance_expense_subitem', 'entertain'
  UNION ALL SELECT 4, '餐饮', 'entertain.meal', 'finance_expense_subitem', 'entertain'
  UNION ALL SELECT 5, '机票', 'travel.flight', 'finance_expense_subitem', 'travel'
  UNION ALL SELECT 6, '火车票', 'travel.train', 'finance_expense_subitem', 'travel'
  UNION ALL SELECT 7, '住宿', 'travel.hotel', 'finance_expense_subitem', 'travel'
  UNION ALL SELECT 8, '市内交通', 'travel.local', 'finance_expense_subitem', 'travel'
  UNION ALL SELECT 9, 'AI充值', 'office.ai', 'finance_expense_subitem', 'office'
) d
WHERE NOT EXISTS (
  SELECT 1 FROM system_dict_data x WHERE x.dict_type=d.dtype AND x.value=d.value AND x.deleted=b'0'
);
