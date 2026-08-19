-- 停用付款旧费用项目可选项（幂等）
-- 付款产品名称改用 finance_product_type；不删除 finance_payment_application 历史行。
SET NAMES utf8mb4;

-- 停用仍启用的 finance_cost_project 可选项（不删字典类型、不删付款行）
UPDATE `system_dict_data`
SET `status` = 1, `updater` = 'admin', `update_time` = NOW()
WHERE `dict_type` = 'finance_cost_project'
  AND `deleted` = b'0'
  AND `status` <> 1;

-- 标记字典类型已退役
UPDATE `system_dict_type`
SET `remark` = '已停用：付款产品名称改用 finance_product_type',
    `updater` = 'admin',
    `update_time` = NOW()
WHERE `type` = 'finance_cost_project'
  AND `deleted` = b'0';
