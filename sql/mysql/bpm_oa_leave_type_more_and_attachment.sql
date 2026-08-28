-- 请假类型补产假等；请假表增加附件。幂等。
SET NAMES utf8mb4;

INSERT INTO `system_dict_data`
    (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`,
     `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 5, '产假', '6', 'bpm_oa_leave_type', 0, 'pink', '', NULL,
       '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data`
    WHERE `deleted` = b'0' AND `dict_type` = 'bpm_oa_leave_type' AND `value` = '6'
);

INSERT INTO `system_dict_data`
    (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`,
     `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 6, '产检假', '7', 'bpm_oa_leave_type', 0, 'pink', '', NULL,
       '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data`
    WHERE `deleted` = b'0' AND `dict_type` = 'bpm_oa_leave_type' AND `value` = '7'
);

INSERT INTO `system_dict_data`
    (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`,
     `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 7, '陪产假', '8', 'bpm_oa_leave_type', 0, 'cyan', '', NULL,
       '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data`
    WHERE `deleted` = b'0' AND `dict_type` = 'bpm_oa_leave_type' AND `value` = '8'
);

INSERT INTO `system_dict_data`
    (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`,
     `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 8, '育儿假', '9', 'bpm_oa_leave_type', 0, 'cyan', '', NULL,
       '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data`
    WHERE `deleted` = b'0' AND `dict_type` = 'bpm_oa_leave_type' AND `value` = '9'
);

INSERT INTO `system_dict_data`
    (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`,
     `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 9, '丧假', '10', 'bpm_oa_leave_type', 0, 'default', '', NULL,
       '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data`
    WHERE `deleted` = b'0' AND `dict_type` = 'bpm_oa_leave_type' AND `value` = '10'
);

INSERT INTO `system_dict_data`
    (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`,
     `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 10, '其他', '11', 'bpm_oa_leave_type', 0, 'default', '', NULL,
       '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data`
    WHERE `deleted` = b'0' AND `dict_type` = 'bpm_oa_leave_type' AND `value` = '11'
);

SET @ddl := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bpm_oa_leave' AND COLUMN_NAME = 'attachment_urls') = 0,
        'ALTER TABLE bpm_oa_leave ADD COLUMN attachment_urls JSON NULL COMMENT ''附件 URL 数组'' AFTER reason',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
