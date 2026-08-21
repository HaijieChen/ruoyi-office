-- OA 请假类型补年假、调休。不重复插入。
SET NAMES utf8mb4;

INSERT INTO `system_dict_data`
    (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`,
     `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 3, '年假', '4', 'bpm_oa_leave_type', 0, 'success', '', NULL,
       '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data`
    WHERE `deleted` = b'0' AND `dict_type` = 'bpm_oa_leave_type' AND `value` = '4'
);

INSERT INTO `system_dict_data`
    (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`,
     `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 4, '调休', '5', 'bpm_oa_leave_type', 0, 'default', '', NULL,
       '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data`
    WHERE `deleted` = b'0' AND `dict_type` = 'bpm_oa_leave_type' AND `value` = '5'
);
