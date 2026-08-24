-- 无票/红冲只走统一发起：侧栏菜单隐藏，路由与权限保留
SET NAMES utf8mb4;

UPDATE `system_menu`
SET `visible` = b'0', `update_time` = NOW()
WHERE `deleted` = b'0'
  AND `component` IN (
      'finance/invoice-redflush/index',
      'finance/expense-reimbursement/no-invoice-create'
  );
