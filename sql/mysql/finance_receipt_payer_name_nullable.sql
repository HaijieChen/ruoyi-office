-- 银行到款：非业务款允许付款方为空（与 form-tighten CF9 对齐）
-- 幂等：将 payer_name 从 NOT NULL 改为可空
SET NAMES utf8mb4;

ALTER TABLE `finance_bank_receipt`
  MODIFY COLUMN `payer_name` varchar(255) NULL DEFAULT NULL COMMENT '付款方名称（业务款必填；非业务款可空）';
