-- 合同签约 / 开票共用产品类型字典（来源：下拉框选项.xlsx）
SET NAMES utf8mb4;

INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '产品类型', 'finance_product_type', 0, '合同签约、开票产品类型', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_type`
    WHERE `type` = 'finance_product_type' AND `deleted` = b'0'
);

UPDATE `system_dict_data`
SET `deleted` = b'1', `updater` = 'admin', `update_time` = NOW()
WHERE `dict_type` = 'finance_product_type'
  AND `deleted` = b'0'
  AND `value` NOT IN (
    '软件', '搜索', '风灵月影', '独代', '服务商', '小红书',
    '游戏联运', '媒体代理', '营销业务', '硬件', 'CDK', '游戏发行'
  );

UPDATE `system_dict_data`
SET `deleted` = b'0', `status` = 0, `updater` = 'admin', `update_time` = NOW()
WHERE `dict_type` = 'finance_product_type'
  AND `value` IN (
    '软件', '搜索', '风灵月影', '独代', '服务商', '小红书',
    '游戏联运', '媒体代理', '营销业务', '硬件', 'CDK', '游戏发行'
  );

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT d.sort, d.label, d.value, d.dict_type, 0, '', '', '', 'admin', NOW(), 'admin', NOW(), b'0'
FROM (
    SELECT 1 AS sort, '软件' AS label, '软件' AS value, 'finance_product_type' AS dict_type
    UNION ALL SELECT 2, '搜索', '搜索', 'finance_product_type'
    UNION ALL SELECT 3, '风灵月影', '风灵月影', 'finance_product_type'
    UNION ALL SELECT 4, '独代', '独代', 'finance_product_type'
    UNION ALL SELECT 5, '服务商', '服务商', 'finance_product_type'
    UNION ALL SELECT 6, '小红书', '小红书', 'finance_product_type'
    UNION ALL SELECT 7, '游戏联运', '游戏联运', 'finance_product_type'
    UNION ALL SELECT 8, '媒体代理', '媒体代理', 'finance_product_type'
    UNION ALL SELECT 9, '营销业务', '营销业务', 'finance_product_type'
    UNION ALL SELECT 10, '硬件', '硬件', 'finance_product_type'
    UNION ALL SELECT 11, 'CDK', 'CDK', 'finance_product_type'
    UNION ALL SELECT 12, '游戏发行', '游戏发行', 'finance_product_type'
) d
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` x
    WHERE x.`deleted` = b'0' AND x.`dict_type` = d.dict_type AND x.`value` = d.value
);
