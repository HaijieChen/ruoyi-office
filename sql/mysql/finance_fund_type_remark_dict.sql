-- 银行到款：款项类型备注字典 finance_fund_type_remark（其他收益-政府补助 / 利息收入 / 其他应付款）
-- 幂等可重复执行；value 存中文，与表单/导入一致，便于后续在字典管理扩展

SET NAMES utf8mb4;

-- 1) 字典类型
INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '财务款项类型备注', 'finance_fund_type_remark', 0, '银行到款 fundTypeRemark：其他收益-政府补助/利息收入/其他应付款', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_type` WHERE `type` = 'finance_fund_type_remark' AND `deleted` = b'0'
);

-- 最新下拉框选项不再提供旧的“往来款项”。历史记录保留，但旧选项停用。
UPDATE `system_dict_data`
SET `deleted` = b'1', `updater` = '1', `update_time` = NOW()
WHERE `dict_type` = 'finance_fund_type_remark'
  AND `value` = '往来款项'
  AND `deleted` = b'0';

UPDATE `system_dict_data`
SET `sort` = CASE `value`
                 WHEN '其他收益-政府补助' THEN 1
                 WHEN '利息收入' THEN 2
                 WHEN '其他应付款' THEN 3
             END,
    `deleted` = b'0', `status` = 0, `updater` = '1', `update_time` = NOW()
WHERE `dict_type` = 'finance_fund_type_remark'
  AND `value` IN ('其他收益-政府补助', '利息收入', '其他应付款');

-- 2) 字典数据（中文 value，与界面/导入一致）
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 1, '其他收益-政府补助', '其他收益-政府补助', 'finance_fund_type_remark', 0, 'default', '', '其他收益-政府补助', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'finance_fund_type_remark' AND `value` = '其他收益-政府补助' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 2, '利息收入', '利息收入', 'finance_fund_type_remark', 0, 'default', '', '利息收入', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'finance_fund_type_remark' AND `value` = '利息收入' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 3, '其他应付款', '其他应付款', 'finance_fund_type_remark', 0, 'default', '', '其他应付款', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'finance_fund_type_remark' AND `value` = '其他应付款' AND `deleted` = b'0'
);
