-- 开票申请/商务单选填多附件。先执行此脚本，再发布后端、前端。
-- dev/test 共库只需执行一次。幂等；历史记录保持 NULL。
SET NAMES utf8mb4;

SET @ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'finance_business_order' AND column_name = 'attachment_file_urls'),
  'SELECT 1',
  'ALTER TABLE finance_business_order ADD COLUMN attachment_file_urls TEXT NULL COMMENT ''申请附件URL列表JSON'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'finance_invoice_application' AND column_name = 'attachment_file_urls'),
  'SELECT 1',
  'ALTER TABLE finance_invoice_application ADD COLUMN attachment_file_urls TEXT NULL COMMENT ''申请附件URL列表JSON，与办票文件分离'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
