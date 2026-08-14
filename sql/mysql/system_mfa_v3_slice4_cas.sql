-- ADR-MFA-v3 切片 4：flow/factor 跨实例 CAS 列补齐（可重复执行）
-- 对齐 system_mfa_v3_slice2_flow.sql；不默认启用 OPTIONAL/REQUIRED

SET @db := DATABASE();

SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `system_mfa_factor` ADD COLUMN `factor_key` varchar(64) NOT NULL DEFAULT '''' COMMENT ''对外因子 ID'' AFTER `user_id`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'system_mfa_factor' AND COLUMN_NAME = 'factor_key'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 空库 / 已有表：确保 flow 与 factor 表存在（幂等）
CREATE TABLE IF NOT EXISTS `system_mfa_auth_flow` (
  `id` varchar(64) NOT NULL,
  `flow_token_hash` char(64) NOT NULL,
  `token_class` varchar(32) NOT NULL,
  `state` varchar(32) NOT NULL DEFAULT 'ACTIVE',
  `tenant_id` bigint NULL DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `client_id` varchar(128) NULL DEFAULT NULL,
  `global_policy_epoch` bigint NOT NULL DEFAULT 0,
  `tenant_policy_epoch` bigint NOT NULL DEFAULT 0,
  `assurance_epoch` bigint NOT NULL DEFAULT 0,
  `allowed_actions` varchar(512) NULL DEFAULT NULL,
  `allowed_factor_ids` varchar(512) NULL DEFAULT NULL,
  `attempts` int NOT NULL DEFAULT 0,
  `expires_at` datetime NOT NULL,
  `creator` varchar(64) NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mfa_flow_token_hash` (`flow_token_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MFA auth flow 权威';

CREATE TABLE IF NOT EXISTS `system_mfa_factor` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `factor_key` varchar(64) NOT NULL DEFAULT '',
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
  PRIMARY KEY (`id`),
  KEY `idx_mfa_factor_user_key` (`tenant_id`, `user_id`, `factor_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MFA 因子权威';
