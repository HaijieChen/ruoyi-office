-- 红冲 BPM 自定义表单路径（幂等）
SET NAMES utf8mb4;

UPDATE `bpm_process_definition_info`
SET `form_custom_view_path` = '/finance/invoice-redflush/info/index',
    `form_custom_create_path` = NULL,
    `update_time` = NOW()
WHERE `deleted` = b'0'
  AND (
    `process_definition_id` LIKE 'finance_invoice_redflush_apply%'
    OR `form_custom_view_path` LIKE '%invoice-redflush%'
  );
