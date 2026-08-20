-- 出差同行多人（幂等）。旧 companion_user_id 迁到 companion_user_ids。
SET NAMES utf8mb4;

SET @sql := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bpm_oa_business_trip' AND COLUMN_NAME = 'companion_user_ids'
    ),
    'SELECT 1',
    'ALTER TABLE bpm_oa_business_trip ADD COLUMN companion_user_ids VARCHAR(1024) NULL COMMENT ''同行人员用户编号，逗号分隔'' AFTER companion_user_id'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE bpm_oa_business_trip
SET companion_user_ids = CAST(companion_user_id AS CHAR)
WHERE deleted = b'0'
  AND companion_user_id IS NOT NULL
  AND (companion_user_ids IS NULL OR companion_user_ids = '');
