-- 飞书社交类型（幂等）
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 50, '飞书', '50', 'system_social_type', 0, '', '', '', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'system_social_type' AND `value` = '50' AND `deleted` = b'0'
);
