-- 出差地点/原因/同行人（幂等）
SET NAMES utf8mb4;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bpm_oa_business_trip' AND COLUMN_NAME = 'destination') = 0,
    'ALTER TABLE bpm_oa_business_trip ADD COLUMN destination VARCHAR(128) NULL COMMENT ''出差地点'' AFTER type',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bpm_oa_business_trip' AND COLUMN_NAME = 'reason') = 0,
    'ALTER TABLE bpm_oa_business_trip ADD COLUMN reason VARCHAR(500) NULL COMMENT ''出差原因'' AFTER destination',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bpm_oa_business_trip' AND COLUMN_NAME = 'companion_user_id') = 0,
    'ALTER TABLE bpm_oa_business_trip ADD COLUMN companion_user_id BIGINT NULL COMMENT ''同行人员用户编号'' AFTER reason',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

ALTER TABLE bpm_oa_business_trip MODIFY COLUMN `type` tinyint NULL COMMENT '出差类型（历史，新单不填）';
