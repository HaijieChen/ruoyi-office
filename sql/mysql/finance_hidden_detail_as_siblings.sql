-- 隐藏详情页必须和列表同级（挂 fin-biz），不能当列表子页，否则点列表会落到详情
SET NAMES utf8mb4;

SET @fin_biz := (
  SELECT id FROM `system_menu`
  WHERE `deleted` = b'0' AND `path` = 'fin-biz'
    AND `parent_id` IN (
      SELECT id FROM `system_menu` WHERE `deleted` = b'0' AND `parent_id` = 0 AND `path` IN ('finance', '/finance')
    )
  LIMIT 1
);

UPDATE `system_menu`
SET `parent_id` = @fin_biz,
    `sort` = 91,
    `visible` = b'0',
    `update_time` = NOW()
WHERE `deleted` = b'0' AND `component` = 'finance/invoice-redflush/info/index';

UPDATE `system_menu`
SET `parent_id` = @fin_biz,
    `sort` = 92,
    `visible` = b'0',
    `update_time` = NOW()
WHERE `deleted` = b'0' AND `component` = 'finance/salary-payment/detail/index';

UPDATE `system_menu`
SET `parent_id` = @fin_biz,
    `sort` = 93,
    `visible` = b'0',
    `update_time` = NOW()
WHERE `deleted` = b'0' AND `component` = 'finance/tax-payment/detail/index';
