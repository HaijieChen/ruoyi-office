-- EXP-87 F2：薪资/税金付款 BPM 审批详情 form_custom_view_path（幂等）
-- 部署 BPM 定义后执行；按 process_definition_id 前缀匹配

SET NAMES utf8mb4;

-- 薪资付款
UPDATE `bpm_process_definition_info`
SET `form_custom_view_path` = '/finance/salary-payment/detail/index',
    `form_custom_create_path` = NULL,
    `update_time` = NOW()
WHERE `deleted` = b'0'
  AND (
    `process_definition_id` LIKE 'finance_salary_payment_apply%'
    OR `form_custom_view_path` LIKE '%salary-payment%'
  );

-- 税金付款
UPDATE `bpm_process_definition_info`
SET `form_custom_view_path` = '/finance/tax-payment/detail/index',
    `form_custom_create_path` = NULL,
    `update_time` = NOW()
WHERE `deleted` = b'0'
  AND (
    `process_definition_id` LIKE 'finance_tax_payment_apply%'
    OR `form_custom_view_path` LIKE '%tax-payment%'
  );
