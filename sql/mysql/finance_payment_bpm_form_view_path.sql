-- 付款申请 BPM 自定义表单路径（幂等说明）
-- form_custom_view_path：审批详情业务页（P-shell 用）
-- form_custom_create_path：【已废弃作 catalog 主路径】
--   通用发起一律留在 BPM 壳内嵌 FormBody（key=finance_payment_apply），
--   不再 router.push(?openCreate=1) 列表弹窗。create path 可空或忽略。
-- 部署流程后运维在设计器 meta 或执行：

UPDATE `bpm_process_definition_info`
SET `form_custom_view_path` = '/finance/payment-application/detail/index',
    -- 清空 create path，避免误导；FE 壳内嵌不依赖此字段
    `form_custom_create_path` = NULL,
    `update_time` = NOW()
WHERE `deleted` = b'0'
  AND (
    `process_definition_id` LIKE 'finance_payment_apply%'
    OR `form_custom_view_path` LIKE '%payment-application%'
    OR `form_custom_create_path` LIKE '%payment-application%'
  );
