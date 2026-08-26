-- 出差业务类型字段与字典（幂等）。不改写历史 type 1–4（市内/省内/省外/国外）。
SET NAMES utf8mb4;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bpm_oa_business_trip' AND COLUMN_NAME = 'origin_city') = 0,
    'ALTER TABLE bpm_oa_business_trip ADD COLUMN origin_city VARCHAR(128) NULL COMMENT ''出发城市'' AFTER destination',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bpm_oa_business_trip' AND COLUMN_NAME = 'biz_type') = 0,
    'ALTER TABLE bpm_oa_business_trip ADD COLUMN biz_type TINYINT NULL COMMENT ''业务类型：1洽谈 2活动 3其他'' AFTER origin_city',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bpm_oa_business_trip' AND COLUMN_NAME = 'transport') = 0,
    'ALTER TABLE bpm_oa_business_trip ADD COLUMN transport VARCHAR(32) NULL COMMENT ''交通工具'' AFTER biz_type',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bpm_oa_business_trip' AND COLUMN_NAME = 'hotel_booking') = 0,
    'ALTER TABLE bpm_oa_business_trip ADD COLUMN hotel_booking VARCHAR(32) NULL COMMENT ''机酒预定情况'' AFTER transport',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bpm_oa_business_trip' AND COLUMN_NAME = 'party_name') = 0,
    'ALTER TABLE bpm_oa_business_trip ADD COLUMN party_name VARCHAR(200) NULL COMMENT ''公司全称或活动邀请方'' AFTER hotel_booking',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bpm_oa_business_trip' AND COLUMN_NAME = 'address') = 0,
    'ALTER TABLE bpm_oa_business_trip ADD COLUMN address VARCHAR(500) NULL COMMENT ''具体地址'' AFTER party_name',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bpm_oa_business_trip' AND COLUMN_NAME = 'contact_info') = 0,
    'ALTER TABLE bpm_oa_business_trip ADD COLUMN contact_info VARCHAR(500) NULL COMMENT ''对接人姓名职务联系方式'' AFTER address',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bpm_oa_business_trip' AND COLUMN_NAME = 'need_output') = 0,
    'ALTER TABLE bpm_oa_business_trip ADD COLUMN need_output VARCHAR(16) NULL COMMENT ''是否需要内容产出'' AFTER contact_info',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bpm_oa_business_trip' AND COLUMN_NAME = 'has_carriage_fee') = 0,
    'ALTER TABLE bpm_oa_business_trip ADD COLUMN has_carriage_fee VARCHAR(16) NULL COMMENT ''是否有车马费'' AFTER need_output',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bpm_oa_business_trip' AND COLUMN_NAME = 'remark') = 0,
    'ALTER TABLE bpm_oa_business_trip ADD COLUMN remark VARCHAR(500) NULL COMMENT ''备注'' AFTER has_carriage_fee',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bpm_oa_business_trip' AND COLUMN_NAME = 'attachment_urls') = 0,
    'ALTER TABLE bpm_oa_business_trip ADD COLUMN attachment_urls JSON NULL COMMENT ''附件 URL 数组'' AFTER remark',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 'OA 出差业务类型', 'bpm_oa_trip_biz_type', 0, '业务洽谈/商务活动/其他类型', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type` = 'bpm_oa_trip_biz_type' AND `deleted` = b'0');

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 1, '业务洽谈', '1', 'bpm_oa_trip_biz_type', 0, 'primary', '', '', '1', NOW(), '1', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'bpm_oa_trip_biz_type' AND `value` = '1' AND `deleted` = b'0');
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 2, '商务活动', '2', 'bpm_oa_trip_biz_type', 0, 'success', '', '', '1', NOW(), '1', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'bpm_oa_trip_biz_type' AND `value` = '2' AND `deleted` = b'0');
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 3, '其他类型', '3', 'bpm_oa_trip_biz_type', 0, 'default', '', '', '1', NOW(), '1', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'bpm_oa_trip_biz_type' AND `value` = '3' AND `deleted` = b'0');

INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 'OA 出差交通工具', 'bpm_oa_trip_transport', 0, '飞机/火车/汽车/自驾', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type` = 'bpm_oa_trip_transport' AND `deleted` = b'0');

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 1, '飞机', '1', 'bpm_oa_trip_transport', 0, 'primary', '', '', '1', NOW(), '1', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'bpm_oa_trip_transport' AND `value` = '1' AND `deleted` = b'0');
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 2, '火车', '2', 'bpm_oa_trip_transport', 0, 'success', '', '', '1', NOW(), '1', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'bpm_oa_trip_transport' AND `value` = '2' AND `deleted` = b'0');
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 3, '汽车', '3', 'bpm_oa_trip_transport', 0, 'warning', '', '', '1', NOW(), '1', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'bpm_oa_trip_transport' AND `value` = '3' AND `deleted` = b'0');
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 4, '自驾', '4', 'bpm_oa_trip_transport', 0, 'default', '', '', '1', NOW(), '1', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'bpm_oa_trip_transport' AND `value` = '4' AND `deleted` = b'0');

INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 'OA 出差机酒预定', 'bpm_oa_trip_hotel_booking', 0, '对方包机酒等四档', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type` = 'bpm_oa_trip_hotel_booking' AND `deleted` = b'0');

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 1, '对方包机酒', '1', 'bpm_oa_trip_hotel_booking', 0, '', '', '', '1', NOW(), '1', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'bpm_oa_trip_hotel_booking' AND `value` = '1' AND `deleted` = b'0');
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 2, '对方包交通费，酒店自行预定报销', '2', 'bpm_oa_trip_hotel_booking', 0, '', '', '', '1', NOW(), '1', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'bpm_oa_trip_hotel_booking' AND `value` = '2' AND `deleted` = b'0');
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 3, '对方包酒店，交通费自行预定报销', '3', 'bpm_oa_trip_hotel_booking', 0, '', '', '', '1', NOW(), '1', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'bpm_oa_trip_hotel_booking' AND `value` = '3' AND `deleted` = b'0');
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 4, '对方不包机酒，自行预定报销', '4', 'bpm_oa_trip_hotel_booking', 0, '', '', '', '1', NOW(), '1', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'bpm_oa_trip_hotel_booking' AND `value` = '4' AND `deleted` = b'0');
