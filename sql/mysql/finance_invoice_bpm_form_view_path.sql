-- 开票申请 BPM 自定义表单路径（幂等）
--
-- 语义分离（重要）：
-- - form_custom_create_path：Vue **路由** path（可带 query）
--   发起流程 catalog 会 router.push；须已注册菜单路由
--   openCreate=1 → 列表页自动打开「提交开票申请」弹窗
-- - form_custom_view_path：审批详情 BusinessFormComponent 的 **组件路径**
--   指向只读详情 views/finance/invoice-application/info/index.vue

UPDATE `bpm_process_definition_info`
SET `form_custom_view_path` = '/finance/invoice-application/info/index',
    `form_custom_create_path` = '/finance/invoice-application?openCreate=1',
    `update_time` = NOW()
WHERE `deleted` = b'0'
  AND (
    `process_definition_id` LIKE 'finance_invoice_apply%'
    OR `form_custom_view_path` LIKE '%invoice-application%'
    OR `form_custom_create_path` LIKE '%invoice-application%'
  );

-- 设计器模型 meta：
--   formCustomCreatePath = /finance/invoice-application?openCreate=1
--   formCustomViewPath   = /finance/invoice-application/info/index
