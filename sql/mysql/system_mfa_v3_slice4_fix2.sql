-- ADR-MFA-v3 切片 4 fix2：碰撞安全回填后再加 UNIQUE（可重复执行）
-- 修复 fix1：已有 legacy-1 与空键 id=1 回填碰撞导致 1062，UNIQUE 未落地、双行同 key

SET @db := DATABASE();

-- 1) 空键：永远不用单纯 legacy-{id}，避免与已有 key 冲突
UPDATE `system_mfa_factor`
SET `factor_key` = CONCAT('legacy-', `id`, '-', REPLACE(UUID(), '-', ''))
WHERE `factor_key` IS NULL OR TRIM(`factor_key`) = '';

-- 2) 已存在的同 (tenant,user,key) 重复行改名，失败不得留下双行同 key
UPDATE `system_mfa_factor` t
INNER JOIN (
  SELECT `id`,
         ROW_NUMBER() OVER (PARTITION BY `tenant_id`, `user_id`, `factor_key` ORDER BY `id`) AS rn
  FROM `system_mfa_factor`
  WHERE IFNULL(`deleted`, 0) = 0
) d ON t.`id` = d.`id`
SET t.`factor_key` = CONCAT(t.`factor_key`, '-d', t.`id`)
WHERE d.rn > 1;

-- 3) 成功路径才加 UNIQUE
SET @sql := (
  SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `system_mfa_factor` DROP INDEX `idx_mfa_factor_user_key`',
    'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'system_mfa_factor' AND INDEX_NAME = 'idx_mfa_factor_user_key'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `system_mfa_factor` ADD UNIQUE KEY `uk_mfa_factor_tenant_user_key` (`tenant_id`, `user_id`, `factor_key`)',
    'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'system_mfa_factor' AND INDEX_NAME = 'uk_mfa_factor_tenant_user_key'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- saga 补 refresh_token（TOKEN_ISSUED 重试复用，不再签）
SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `system_mfa_enroll_saga` ADD COLUMN `refresh_token` varchar(255) NULL DEFAULT NULL AFTER `access_token`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'system_mfa_enroll_saga' AND COLUMN_NAME = 'refresh_token'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
