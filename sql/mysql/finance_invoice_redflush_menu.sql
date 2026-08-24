-- 开票红冲菜单/权限（幂等），挂财务目录，紧挨开票申请
SET NAMES utf8mb4;

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '开票申请-红冲', '', 2, 36,
       COALESCE(
           (SELECT id FROM system_menu WHERE deleted=b'0' AND parent_id IN (
               SELECT id FROM system_menu WHERE deleted=b'0' AND parent_id=0 AND path IN ('finance','/finance')
           ) AND path='fin-biz' LIMIT 1),
           (SELECT id FROM system_menu WHERE deleted=b'0' AND type=1 AND parent_id=0 AND path IN ('finance','/finance') LIMIT 1)
       ),
       'invoice-redflush', 'ep:document-delete', 'finance/invoice-redflush/index', 'FinanceInvoiceRedflush',
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'finance/invoice-redflush/index'
);

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '红冲详情', '', 2, 37,
       (SELECT id FROM system_menu WHERE deleted=b'0' AND type=1 AND parent_id=0 AND path IN ('finance','/finance') LIMIT 1),
       'invoice-redflush/info', '', 'finance/invoice-redflush/info/index', 'FinanceInvoiceRedflushInfo',
       0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'finance/invoice-redflush/info/index'
);

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.id, m.id, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM system_role r
CROSS JOIN system_menu m
WHERE r.deleted=b'0' AND r.tenant_id=1 AND m.deleted=b'0'
  AND (
        (r.code='business_staff' AND m.component IN (
            'finance/invoice-redflush/index', 'finance/invoice-redflush/info/index'))
     OR (r.code='finance_admin' AND m.component IN (
            'finance/invoice-redflush/index', 'finance/invoice-redflush/info/index'))
  )
  AND NOT EXISTS (
      SELECT 1 FROM system_role_menu rm
      WHERE rm.role_id=r.id AND rm.menu_id=m.id AND rm.tenant_id=1 AND rm.deleted=b'0'
  );
