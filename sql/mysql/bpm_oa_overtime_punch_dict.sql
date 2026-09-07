-- OA 加班「是否法定节假日」字典（幂等）。否=false 周末链，是=true 节假日链。
SET NAMES utf8mb4;

INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 'OA 加班是否法定节假日', 'bpm_oa_overtime_holiday', 0, '否=周末审批链，是=节假日审批链', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_type` WHERE `type` = 'bpm_oa_overtime_holiday' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 1, '否', 'false', 'bpm_oa_overtime_holiday', 0, 'default', '', '周末加班链', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'bpm_oa_overtime_holiday' AND `value` = 'false' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 2, '是', 'true', 'bpm_oa_overtime_holiday', 0, 'warning', '', '法定节假日加班链', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'bpm_oa_overtime_holiday' AND `value` = 'true' AND `deleted` = b'0'
);
