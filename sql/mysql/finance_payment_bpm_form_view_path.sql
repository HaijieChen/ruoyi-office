-- 付款申请 BPM 自定义表单路径（幂等说明）
-- form_custom_create_path：/finance/payment-application?openCreate=1
-- form_custom_view_path：/finance/payment-application/detail/index
-- 部署流程后运维在设计器 meta 或执行：

UPDATE `bpm_process_definition_info`
SET `form_custom_view_path` = '/finance/payment-application/detail/index',
    `form_custom_create_path` = '/finance/payment-application?openCreate=1',
    `update_time` = NOW()
WHERE `deleted` = b'0'
  AND (
    `process_definition_id` LIKE 'finance_payment_apply%'
    OR `form_custom_view_path` LIKE '%payment-application%'
    OR `form_custom_create_path` LIKE '%payment-application%'
  );
