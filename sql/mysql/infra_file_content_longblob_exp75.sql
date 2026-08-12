-- EXP-75 / TC-SIZE-01：DB 文件 provider 支持 ≥20MB 业务边界
-- 根因：infra_file_content.content = MEDIUMBLOB（≤16MB-1）导致 20MB 入职资料上传 Data truncation → 500
-- 修复：升至 LONGBLOB（≤4GB）；multipart / 业务上限仍为 20MB
-- 幂等：可重复执行；仅当当前类型非 longblob 时 ALTER

-- 【发布前】确认 max_allowed_packet ≥ 32MB（建议 ≥ 64MB）
-- SHOW VARIABLES LIKE 'max_allowed_packet';

SET @col_type := (
  SELECT DATA_TYPE
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'infra_file_content'
    AND COLUMN_NAME = 'content'
  LIMIT 1
);

SET @sql := IF(
  @col_type IS NOT NULL AND LOWER(@col_type) <> 'longblob',
  'ALTER TABLE `infra_file_content` MODIFY COLUMN `content` LONGBLOB NOT NULL COMMENT ''文件内容（EXP-75：支持≥20MB；原 mediumblob 上限 16MB）''',
  'SELECT ''infra_file_content.content already longblob or table missing — skip'' AS info'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 校验
SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, COLUMN_TYPE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'infra_file_content'
  AND COLUMN_NAME = 'content';
