-- 开票申请 BPM 自定义表单路径（幂等）
--
-- 语义分离（重要）：
-- - form_custom_create_path：Vue **路由** path，发起流程 catalog 会 router.push
--   必须是已注册菜单路由，如 /finance/invoice-application（列表页可进；勿指不存在的 /info）
-- - form_custom_view_path：审批详情 BusinessFormComponent 的 **组件路径**（registerComponent includes）
--   指向只读详情 views/finance/invoice-application/info/index.vue
--
-- 若把 create 也写成 /info/index，商务从审批中心→发起流程点「开票申请审批」会 404。

UPDATE `bpm_process_definition_info`
SET `form_custom_view_path` = '/finance/invoice-application/info/index',
    `form_custom_create_path` = '/finance/invoice-application',
    `update_time` = NOW()
WHERE `deleted` = b'0'
  AND (
    `process_definition_id` LIKE 'finance_invoice_apply%'
    OR `form_custom_view_path` LIKE '%invoice-application%'
    OR `form_custom_create_path` LIKE '%invoice-application%'
  );

-- 设计器模型 meta（再发布会写回 definition_info）
-- 应用层/运维：保证 META_INFO_ 中
--   formCustomCreatePath = /finance/invoice-application
--   formCustomViewPath   = /finance/invoice-application/info/index
