-- ADR-MFA-v3 切片 1：契约与权威模型
-- 可重复执行；默认不写入非 OFF 策略（UNINITIALIZED/OFF 安全默认）
-- 支持：空库新建 + 自 v2/旁路同名表演进（policy_version → epoch 列）

-- ========== 1) 空库 / 新环境：创建 v3 表 ==========

CREATE TABLE IF NOT EXISTS `system_mfa_control_state` (
  `id` bigint NOT NULL COMMENT '单例固定为 1',
  `lifecycle_state` varchar(32) NOT NULL DEFAULT 'UNINITIALIZED' COMMENT 'UNINITIALIZED/ARMED/DEGRADED_CLOSED',
  `global_mode` varchar(32) NOT NULL DEFAULT 'OFF' COMMENT 'OFF/OPTIONAL/REQUIRED',
  `global_allowed_factors` varchar(255) NULL DEFAULT NULL COMMENT '逗号分隔因子',
  `global_policy_epoch` bigint NOT NULL DEFAULT 0 COMMENT '全局策略 epoch，单调',
  `global_min_accepted_epoch` bigint NOT NULL DEFAULT 0 COMMENT '最低可接受策略 epoch',
  `armed_at` datetime NULL DEFAULT NULL COMMENT '首次 ARMED 时间',
  `checksum` varchar(128) NULL DEFAULT NULL COMMENT '控制面 checksum',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MFA 控制面单例（含全局策略权威）';

CREATE TABLE IF NOT EXISTS `system_mfa_tenant_policy` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL COMMENT '租户编号',
  `mode` varchar(32) NOT NULL DEFAULT 'INHERIT' COMMENT 'INHERIT/OFF/OPTIONAL/REQUIRED',
  `allowed_factors` varchar(255) NULL DEFAULT NULL,
  `policy_epoch` bigint NOT NULL DEFAULT 0,
  `min_accepted_epoch` bigint NOT NULL DEFAULT 0,
  `checksum` varchar(128) NOT NULL DEFAULT '',
  `confirmed` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否已确认生效',
  `creator` varchar(64) NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mfa_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户 MFA 策略权威';

CREATE TABLE IF NOT EXISTS `system_mfa_user_assurance` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `enabled` bit(1) NOT NULL DEFAULT b'0' COMMENT 'OPTIONAL 下用户 MFA 开关',
  `enrollment_state` varchar(32) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/PENDING/COMPLETED',
  `preferred_factor_id` bigint NULL DEFAULT NULL,
  `assurance_epoch` bigint NOT NULL DEFAULT 0 COMMENT '权威 assurance epoch，禁止猜测缺行',
  `creator` varchar(64) NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mfa_assurance_tenant_user` (`tenant_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户 MFA assurance 权威（含 assurance_epoch）';

-- ========== 2) 自 v2/旁路表幂等升级（表已存在时 ADD 缺列 + 回填） ==========
-- 约定：v2 control 可能有 policy_version/lifecycle_state/checksum；
--       v2 tenant 可能有 policy_version 而无 policy_epoch。

-- control_state: 补 v3 列
SET @db := DATABASE();

SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `system_mfa_control_state` ADD COLUMN `global_mode` varchar(32) NOT NULL DEFAULT ''OFF'' COMMENT ''OFF/OPTIONAL/REQUIRED'' AFTER `lifecycle_state`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'system_mfa_control_state' AND COLUMN_NAME = 'global_mode'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `system_mfa_control_state` ADD COLUMN `global_allowed_factors` varchar(255) NULL DEFAULT NULL AFTER `global_mode`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'system_mfa_control_state' AND COLUMN_NAME = 'global_allowed_factors'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `system_mfa_control_state` ADD COLUMN `global_policy_epoch` bigint NOT NULL DEFAULT 0 AFTER `global_allowed_factors`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'system_mfa_control_state' AND COLUMN_NAME = 'global_policy_epoch'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `system_mfa_control_state` ADD COLUMN `global_min_accepted_epoch` bigint NOT NULL DEFAULT 0 AFTER `global_policy_epoch`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'system_mfa_control_state' AND COLUMN_NAME = 'global_min_accepted_epoch'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 若存在 v2 policy_version 列，回填到 global_policy_epoch（仅当 epoch 仍为 0）
SET @has_pv := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'system_mfa_control_state' AND COLUMN_NAME = 'policy_version'
);
SET @sql := IF(@has_pv > 0,
  'UPDATE `system_mfa_control_state` SET `global_policy_epoch` = COALESCE(`policy_version`, 0) WHERE `global_policy_epoch` = 0',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 安全默认：无 mode 时写 OFF（已 DEFAULT，补行空值）
UPDATE `system_mfa_control_state` SET `global_mode` = 'OFF' WHERE `global_mode` IS NULL OR `global_mode` = '';

-- tenant_policy: 补 epoch 列
SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `system_mfa_tenant_policy` ADD COLUMN `policy_epoch` bigint NOT NULL DEFAULT 0 AFTER `allowed_factors`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'system_mfa_tenant_policy' AND COLUMN_NAME = 'policy_epoch'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `system_mfa_tenant_policy` ADD COLUMN `min_accepted_epoch` bigint NOT NULL DEFAULT 0 AFTER `policy_epoch`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'system_mfa_tenant_policy' AND COLUMN_NAME = 'min_accepted_epoch'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_tpv := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'system_mfa_tenant_policy' AND COLUMN_NAME = 'policy_version'
);
SET @sql := IF(@has_tpv > 0,
  'UPDATE `system_mfa_tenant_policy` SET `policy_epoch` = COALESCE(`policy_version`, 0) WHERE `policy_epoch` = 0',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ========== 3) 自旁路/v2 system_mfa_global_policy 语义合并到 control_state ==========
-- 可重复：仅当 legacy 表存在且有 confirmed 行时执行；不删除 legacy 表。
-- checksum 与 Java MfaChecksumUtil.computeControl 一致：
--   SHA2(lifecycle|mode|factors|epoch|min, 256) 小写 hex

SET @has_legacy_global := (
  SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'system_mfa_global_policy'
);

-- 确保 control 单例行存在（合并前置）
SET @sql := IF(@has_legacy_global > 0,
  'INSERT IGNORE INTO `system_mfa_control_state` (`id`, `lifecycle_state`, `global_mode`, `global_policy_epoch`, `global_min_accepted_epoch`, `checksum`)
   VALUES (1, ''UNINITIALIZED'', ''OFF'', 0, 0, ''uninitialized-off'')',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 将 confirmed legacy global 行翻译进 control（mode/factors/epoch/min/lifecycle/checksum）
SET @sql := IF(@has_legacy_global > 0,
  'UPDATE `system_mfa_control_state` c
   INNER JOIN `system_mfa_global_policy` g ON g.id = 1 AND IFNULL(g.confirmed, 0) = 1 AND IFNULL(g.deleted, 0) = 0
   SET
     c.global_mode = UPPER(TRIM(g.mode)),
     c.global_allowed_factors = g.allowed_factors,
     c.global_policy_epoch = GREATEST(IFNULL(c.global_policy_epoch, 0), IFNULL(g.policy_version, 0)),
     c.global_min_accepted_epoch = CASE
         WHEN UPPER(TRIM(g.mode)) IN (''OPTIONAL'', ''REQUIRED'')
           THEN GREATEST(IFNULL(c.global_min_accepted_epoch, 0), IFNULL(g.policy_version, 0))
         ELSE IFNULL(c.global_min_accepted_epoch, 0)
       END,
     c.lifecycle_state = CASE
         WHEN UPPER(TRIM(g.mode)) IN (''OPTIONAL'', ''REQUIRED'') THEN ''ARMED''
         WHEN c.lifecycle_state = ''ARMED'' THEN ''ARMED''
         ELSE ''UNINITIALIZED''
       END,
     c.armed_at = CASE
         WHEN UPPER(TRIM(g.mode)) IN (''OPTIONAL'', ''REQUIRED'') AND c.armed_at IS NULL THEN NOW()
         ELSE c.armed_at
       END,
     c.checksum = LOWER(SHA2(CONCAT(
         CASE
           WHEN UPPER(TRIM(g.mode)) IN (''OPTIONAL'', ''REQUIRED'') THEN ''ARMED''
           WHEN c.lifecycle_state = ''ARMED'' THEN ''ARMED''
           ELSE ''UNINITIALIZED''
         END,
         ''|'', UPPER(TRIM(g.mode)), ''|'', IFNULL(g.allowed_factors, ''''), ''|'',
         GREATEST(IFNULL(c.global_policy_epoch, 0), IFNULL(g.policy_version, 0)), ''|'',
         CASE
           WHEN UPPER(TRIM(g.mode)) IN (''OPTIONAL'', ''REQUIRED'')
             THEN GREATEST(IFNULL(c.global_min_accepted_epoch, 0), IFNULL(g.policy_version, 0))
           ELSE IFNULL(c.global_min_accepted_epoch, 0)
         END
       ), 256))',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 对已合并但 checksum 仍为 legacy 占位串的 control 行，按当前列重算（幂等）
UPDATE `system_mfa_control_state` c
SET c.checksum = LOWER(SHA2(CONCAT(
    IFNULL(c.lifecycle_state, 'UNINITIALIZED'), '|',
    IFNULL(c.global_mode, 'OFF'), '|',
    IFNULL(c.global_allowed_factors, ''), '|',
    IFNULL(c.global_policy_epoch, 0), '|',
    IFNULL(c.global_min_accepted_epoch, 0)
  ), 256))
WHERE c.id = 1
  AND c.checksum IS NOT NULL
  AND c.checksum NOT REGEXP '^[0-9a-f]{64}$';

-- 部署后：不默认插入 REQUIRED；空库仍由应用按 UNINITIALIZED 处理。
-- legacy system_mfa_global_policy 保留只读档案，新代码不再读写。
