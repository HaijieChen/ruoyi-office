-- 隐藏详情页从财务根挪到对应列表下，避免点「财务管理」落到无 id 详情
SET NAMES utf8mb4;

UPDATE `system_menu` d
JOIN `system_menu` p
  ON p.`deleted` = b'0'
 AND p.`component` = 'finance/salary-payment/index'
SET d.`parent_id` = p.`id`, d.`update_time` = NOW()
WHERE d.`deleted` = b'0'
  AND d.`component` = 'finance/salary-payment/detail/index';

UPDATE `system_menu` d
JOIN `system_menu` p
  ON p.`deleted` = b'0'
 AND p.`component` = 'finance/tax-payment/index'
SET d.`parent_id` = p.`id`, d.`update_time` = NOW()
WHERE d.`deleted` = b'0'
  AND d.`component` = 'finance/tax-payment/detail/index';

UPDATE `system_menu` d
JOIN `system_menu` p
  ON p.`deleted` = b'0'
 AND p.`component` = 'finance/invoice-application/index'
SET d.`parent_id` = p.`id`, d.`update_time` = NOW()
WHERE d.`deleted` = b'0'
  AND d.`component` = 'finance/invoice-redflush/info/index';
