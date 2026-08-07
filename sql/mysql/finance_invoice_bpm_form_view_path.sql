-- 开票申请 BPM 自定义表单路径（幂等）
--
-- 语义分离（重要）：
-- - form_custom_view_path：审批详情 BusinessFormComponent 的 **组件路径**
--   指向只读详情 views/finance/invoice-application/info/index.vue
-- - form_custom_create_path：【已废弃作 catalog 主路径】
--   通用发起一律壳内嵌 FormBody（key=finance_invoice_apply），
--   不再 router.push(?openCreate=1)。create path 可空或忽略。

UPDATE `bpm_process_definition_info`
SET `form_custom_view_path` = '/finance/invoice-application/info/index',
    `form_custom_create_path` = NULL,
    `update_time` = NOW()
WHERE `deleted` = b'0'
  AND (
    `process_definition_id` LIKE 'finance_invoice_apply%'
    OR `form_custom_view_path` LIKE '%invoice-application%'
    OR `form_custom_create_path` LIKE '%invoice-application%'
  );

-- 设计器模型 meta：
--   formCustomCreatePath = （catalog 忽略；壳内注册表）
--   formCustomViewPath   = /finance/invoice-application/info/index
