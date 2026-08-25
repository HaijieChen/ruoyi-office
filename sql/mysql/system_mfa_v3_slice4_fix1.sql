-- ADR-MFA-v3 切片 4 fix1：factor_key 安全回填 + 唯一约束 + enroll saga
-- 禁止把旧行统一写成空串；不默认启用 OPTIONAL/REQUIRED

SET @db := DATABASE();

CREATE TABLE IF NOT EXISTS `system_mfa_factor` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `factor_key` varchar(64) NOT NULL,
  `type` varchar(32) NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `label` varchar(128) NULL DEFAULT NULL,
  `destination_hash` varchar(128) NULL DEFAULT NULL,
  `destination_masked` varchar(128) NULL DEFAULT NULL,
  `secret_ciphertext` varchar(512) NULL DEFAULT NULL,
  `key_id` varchar(64) NULL DEFAULT NULL,
  `last_used_step` bigint NULL DEFAULT NULL,
  `verified_at` datetime NULL DEFAULT NULL,
  `creator` varchar(64) NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 缺列则补（已有 slice4 空默认）
SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `system_mfa_factor` ADD COLUMN `factor_key` varchar(64) NULL COMMENT ''对外因子 ID'' AFTER `user_id`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'system_mfa_factor' AND COLUMN_NAME = 'factor_key'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 安全回填：禁止写成空串；带 UUID 后缀，避免与已有 legacy-{id} 碰撞
UPDATE `system_mfa_factor`
SET `factor_key` = CONCAT('legacy-', `id`, '-', REPLACE(UUID(), '-', ''))
WHERE `factor_key` IS NULL OR TRIM(`factor_key`) = '';

-- 去掉旧非唯一索引（若存在）
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

CREATE TABLE IF NOT EXISTS `system_mfa_enroll_saga` (
  `flow_token_hash` char(64) NOT NULL,
  `tenant_id` bigint NULL DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `factor_id` varchar(64) NOT NULL,
  `totp_step` bigint NULL DEFAULT NULL,
  `expected_epoch` bigint NOT NULL DEFAULT 0,
  `access_token` varchar(255) NULL DEFAULT NULL,
  `state` varchar(32) NOT NULL,
  `creator` varchar(64) NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`flow_token_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MFA enroll 可重启补偿日志';
