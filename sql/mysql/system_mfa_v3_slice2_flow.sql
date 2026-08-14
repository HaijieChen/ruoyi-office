-- ADR-MFA-v3 切片 2：auth flow / factor / 挑战会话表
-- 可重复执行；默认不启用 OPTIONAL/REQUIRED 策略

CREATE TABLE IF NOT EXISTS `system_mfa_auth_flow` (
  `id` varchar(64) NOT NULL COMMENT 'flow id',
  `flow_token_hash` char(64) NOT NULL COMMENT 'SHA-256 hex of raw flowToken',
  `token_class` varchar(32) NOT NULL COMMENT 'PRE_AUTH/ENROLLMENT/RECOVERY',
  `state` varchar(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/COMPLETED/EXPIRED/REVOKED',
  `tenant_id` bigint NULL DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `client_id` varchar(128) NULL DEFAULT NULL,
  `global_policy_epoch` bigint NOT NULL DEFAULT 0,
  `tenant_policy_epoch` bigint NOT NULL DEFAULT 0,
  `assurance_epoch` bigint NOT NULL DEFAULT 0,
  `allowed_actions` varchar(512) NULL DEFAULT NULL COMMENT 'comma-separated',
  `allowed_factor_ids` varchar(512) NULL DEFAULT NULL COMMENT 'comma-separated',
  `attempts` int NOT NULL DEFAULT 0,
  `expires_at` datetime NOT NULL,
  `creator` varchar(64) NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mfa_flow_token_hash` (`flow_token_hash`),
  KEY `idx_mfa_flow_user` (`tenant_id`, `user_id`, `state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MFA auth flow 权威（切片 2）';

CREATE TABLE IF NOT EXISTS `system_mfa_factor` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `type` varchar(32) NOT NULL COMMENT 'TOTP/SMS/EMAIL/BACKUP_CODE',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACTIVE/DISABLED',
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
  KEY `idx_mfa_factor_user` (`tenant_id`, `user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MFA 因子权威（切片 2 最小表）';

CREATE TABLE IF NOT EXISTS `system_mfa_issuance_decision` (
  `id` varchar(64) NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'READY' COMMENT 'READY/CONSUMED',
  `source_type` varchar(32) NOT NULL COMMENT 'FLOW/CODE/REFRESH',
  `source_id` varchar(64) NOT NULL,
  `subject_class` varchar(32) NOT NULL DEFAULT 'ADMIN_USER',
  `tenant_id` bigint NULL DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `client_id` varchar(128) NULL DEFAULT NULL,
  `amr` varchar(255) NULL DEFAULT NULL,
  `mfa_satisfied` bit(1) NOT NULL DEFAULT b'0',
  `enrollment_complete` bit(1) NOT NULL DEFAULT b'1',
  `global_policy_epoch` bigint NOT NULL DEFAULT 0,
  `tenant_policy_epoch` bigint NOT NULL DEFAULT 0,
  `assurance_epoch` bigint NOT NULL DEFAULT 0,
  `expires_at` datetime NOT NULL,
  `creator` varchar(64) NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_mfa_decision_source` (`source_type`, `source_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='T_issue Decision 权威（切片 2）';
