-- 开票申请 BPM：自定义表单「查看」路径改为只读详情组件（非列表）
-- 组件：views/finance/invoice-application/info/index.vue
-- registerComponent 用 includes 匹配，须带 /info 以免命中列表 index
-- 幂等：可重复执行

UPDATE `bpm_process_definition_info`
SET `form_custom_view_path` = '/finance/invoice-application/info/index',
    `update_time` = NOW()
WHERE `deleted` = b'0'
  AND (
    `form_custom_view_path` LIKE '%invoice-application%'
    OR `form_custom_create_path` LIKE '%invoice-application%'
    OR `process_definition_id` LIKE 'finance_invoice_apply%'
  )
  AND (
    `form_custom_view_path` IS NULL
    OR `form_custom_view_path` <> '/finance/invoice-application/info/index'
  );

-- 若模型 meta 内嵌路径（JSON 文本），一并纠正常见错误指向
UPDATE `bpm_process_definition_info`
SET `form_custom_create_path` = '/finance/invoice-application/info/index'
WHERE `deleted` = b'0'
  AND `form_custom_create_path` IN (
    '/finance/invoice-application',
    '/finance/invoice-application/index',
    'finance/invoice-application',
    'finance/invoice-application/index'
  );

-- 注意：create 路径若业务侧只走菜单「提交开票申请」API，create path 影响较小；
-- 查看路径是审批办理必修。上面 create 也指到 info 可避免误嵌列表；新建仍靠业务入口。

-- 设计器模型 meta（再发布时会写回 definition_info）
-- MySQL 8 JSON：有 JSON 函数时可用；否则在应用层/手工改 META_INFO_
-- UPDATE ACT_RE_MODEL
-- SET META_INFO_ = REPLACE(REPLACE(META_INFO_,
--   '"formCustomViewPath":"/finance/invoice-application"',
--   '"formCustomViewPath":"/finance/invoice-application/info/index"'),
--   '"formCustomCreatePath":"/finance/invoice-application"',
--   '"formCustomCreatePath":"/finance/invoice-application/info/index"')
-- WHERE KEY_ = 'finance_invoice_apply';
