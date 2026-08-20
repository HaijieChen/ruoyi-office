-- 出差/外出城市字典（幂等）。remark=T1 北上广深 400，OTHER 300。
SET NAMES utf8mb4;

INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 'OA 出差城市', 'oa_travel_city', 0, '北上广深=T1(400)，其他=OTHER(300)', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_type` WHERE `type` = 'oa_travel_city' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 1, '北京', '北京', 'oa_travel_city', 0, 'warning', '', 'T1', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'oa_travel_city' AND `value` = '北京' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 2, '上海', '上海', 'oa_travel_city', 0, 'warning', '', 'T1', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'oa_travel_city' AND `value` = '上海' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 3, '广州', '广州', 'oa_travel_city', 0, 'warning', '', 'T1', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'oa_travel_city' AND `value` = '广州' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 4, '深圳', '深圳', 'oa_travel_city', 0, 'warning', '', 'T1', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'oa_travel_city' AND `value` = '深圳' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 5, '杭州', '杭州', 'oa_travel_city', 0, 'default', '', 'OTHER', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'oa_travel_city' AND `value` = '杭州' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 6, '南京', '南京', 'oa_travel_city', 0, 'default', '', 'OTHER', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'oa_travel_city' AND `value` = '南京' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 7, '苏州', '苏州', 'oa_travel_city', 0, 'default', '', 'OTHER', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'oa_travel_city' AND `value` = '苏州' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 8, '成都', '成都', 'oa_travel_city', 0, 'default', '', 'OTHER', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'oa_travel_city' AND `value` = '成都' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 9, '武汉', '武汉', 'oa_travel_city', 0, 'default', '', 'OTHER', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'oa_travel_city' AND `value` = '武汉' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 10, '西安', '西安', 'oa_travel_city', 0, 'default', '', 'OTHER', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'oa_travel_city' AND `value` = '西安' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 11, '重庆', '重庆', 'oa_travel_city', 0, 'default', '', 'OTHER', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'oa_travel_city' AND `value` = '重庆' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 12, '天津', '天津', 'oa_travel_city', 0, 'default', '', 'OTHER', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'oa_travel_city' AND `value` = '天津' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 13, '青岛', '青岛', 'oa_travel_city', 0, 'default', '', 'OTHER', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'oa_travel_city' AND `value` = '青岛' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 14, '厦门', '厦门', 'oa_travel_city', 0, 'default', '', 'OTHER', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'oa_travel_city' AND `value` = '厦门' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 15, '长沙', '长沙', 'oa_travel_city', 0, 'default', '', 'OTHER', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'oa_travel_city' AND `value` = '长沙' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 16, '郑州', '郑州', 'oa_travel_city', 0, 'default', '', 'OTHER', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'oa_travel_city' AND `value` = '郑州' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 17, '合肥', '合肥', 'oa_travel_city', 0, 'default', '', 'OTHER', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'oa_travel_city' AND `value` = '合肥' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 18, '福州', '福州', 'oa_travel_city', 0, 'default', '', 'OTHER', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'oa_travel_city' AND `value` = '福州' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 19, '南昌', '南昌', 'oa_travel_city', 0, 'default', '', 'OTHER', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'oa_travel_city' AND `value` = '南昌' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 20, '沈阳', '沈阳', 'oa_travel_city', 0, 'default', '', 'OTHER', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'oa_travel_city' AND `value` = '沈阳' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 21, '大连', '大连', 'oa_travel_city', 0, 'default', '', 'OTHER', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'oa_travel_city' AND `value` = '大连' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 22, '哈尔滨', '哈尔滨', 'oa_travel_city', 0, 'default', '', 'OTHER', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'oa_travel_city' AND `value` = '哈尔滨' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 23, '长春', '长春', 'oa_travel_city', 0, 'default', '', 'OTHER', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'oa_travel_city' AND `value` = '长春' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 24, '昆明', '昆明', 'oa_travel_city', 0, 'default', '', 'OTHER', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'oa_travel_city' AND `value` = '昆明' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 25, '贵阳', '贵阳', 'oa_travel_city', 0, 'default', '', 'OTHER', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'oa_travel_city' AND `value` = '贵阳' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 26, '南宁', '南宁', 'oa_travel_city', 0, 'default', '', 'OTHER', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'oa_travel_city' AND `value` = '南宁' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 27, '海口', '海口', 'oa_travel_city', 0, 'default', '', 'OTHER', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'oa_travel_city' AND `value` = '海口' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 28, '乌鲁木齐', '乌鲁木齐', 'oa_travel_city', 0, 'default', '', 'OTHER', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'oa_travel_city' AND `value` = '乌鲁木齐' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 29, '兰州', '兰州', 'oa_travel_city', 0, 'default', '', 'OTHER', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'oa_travel_city' AND `value` = '兰州' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 30, '银川', '银川', 'oa_travel_city', 0, 'default', '', 'OTHER', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'oa_travel_city' AND `value` = '银川' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 31, '西宁', '西宁', 'oa_travel_city', 0, 'default', '', 'OTHER', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'oa_travel_city' AND `value` = '西宁' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 32, '呼和浩特', '呼和浩特', 'oa_travel_city', 0, 'default', '', 'OTHER', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'oa_travel_city' AND `value` = '呼和浩特' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 33, '拉萨', '拉萨', 'oa_travel_city', 0, 'default', '', 'OTHER', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'oa_travel_city' AND `value` = '拉萨' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 34, '其他', '其他', 'oa_travel_city', 0, 'default', '', 'OTHER', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'oa_travel_city' AND `value` = '其他' AND `deleted` = b'0'
);
