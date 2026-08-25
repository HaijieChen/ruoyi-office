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
  `legacy_global_merged` bit(1) NOT NULL DEFAULT b'0' COMMENT 'v2 global_policy 是否已消费（F-R4-02/03）',
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

-- F-R4-02/03：显式消费标记（不得用 64hex 形状推断已迁移；真实 v2 checksum 亦为 64hex）
SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `system_mfa_control_state` ADD COLUMN `legacy_global_merged` bit(1) NOT NULL DEFAULT b''0'' COMMENT ''v2 global_policy 是否已消费'' AFTER `checksum`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'system_mfa_control_state' AND COLUMN_NAME = 'legacy_global_merged'
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
-- F-R4-02：用持久列 legacy_global_merged 作一次性消费标记；禁止用 64hex 形状推断版本
--   （真实 ADR-v2 checksum 亦为 64 位小写 SHA-256）。
-- F-R4-03：不得用 stale legacy OFF 覆盖/重签已有非 OFF 的 control（含损坏 nonhex）；
--   禁止对可疑非 seed 行按当前列重新签名。
-- F-R3-03：factors 字典序后 SHA2，与 Java MfaChecksumUtil.computeControl 一致。
-- 不删除 legacy 表（只读档案）。

SET @has_legacy_global := (
  SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'system_mfa_global_policy'
);

-- 确保 control 单例行存在（合并前置）
SET @sql := IF(@has_legacy_global > 0,
  'INSERT IGNORE INTO `system_mfa_control_state` (`id`, `lifecycle_state`, `global_mode`, `global_policy_epoch`, `global_min_accepted_epoch`, `checksum`, `legacy_global_merged`)
   VALUES (1, ''UNINITIALIZED'', ''OFF'', 0, 0, ''uninitialized-off'', b''0'')',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 将 confirmed legacy global 翻译进 control
-- F-R5-02：仅可证明的 seed 或 v2-OFF→legacy-非OFF 升级路径一次性消费；
--   禁止同 mode 改写 factors / 重签已有或损坏 v3（含 merged 默认 0 的 REQUIRED 行）
SET @sql := IF(@has_legacy_global > 0,
  'UPDATE `system_mfa_control_state` c
   INNER JOIN `system_mfa_global_policy` g
     ON g.id = 1 AND IFNULL(g.confirmed, 0) = 1 AND IFNULL(g.deleted, 0) = 0
   SET
     c.global_mode = UPPER(TRIM(g.mode)),
     c.global_allowed_factors = (
       CASE
         WHEN g.allowed_factors IS NULL OR TRIM(g.allowed_factors) = '''' THEN ''''
         ELSE (
           SELECT GROUP_CONCAT(DISTINCT TRIM(jt.f) ORDER BY TRIM(jt.f) SEPARATOR '','')
           FROM JSON_TABLE(
             CONCAT(''["'', REPLACE(REPLACE(TRIM(g.allowed_factors), ''"'', ''''), '','', ''","''), ''"]''),
             ''$[*]'' COLUMNS (f VARCHAR(64) PATH ''$'')
           ) AS jt
           WHERE TRIM(jt.f) <> ''''
         )
       END
     ),
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
         WHEN c.armed_at IS NOT NULL THEN c.armed_at
         ELSE c.armed_at
       END,
     c.checksum = LOWER(SHA2(CONCAT(
         CASE
           WHEN UPPER(TRIM(g.mode)) IN (''OPTIONAL'', ''REQUIRED'') THEN ''ARMED''
           WHEN c.lifecycle_state = ''ARMED'' THEN ''ARMED''
           ELSE ''UNINITIALIZED''
         END,
         ''|'', UPPER(TRIM(g.mode)), ''|'',
         (
           CASE
             WHEN g.allowed_factors IS NULL OR TRIM(g.allowed_factors) = '''' THEN ''''
             ELSE (
               SELECT GROUP_CONCAT(DISTINCT TRIM(jt2.f) ORDER BY TRIM(jt2.f) SEPARATOR '','')
               FROM JSON_TABLE(
                 CONCAT(''["'', REPLACE(REPLACE(TRIM(g.allowed_factors), ''"'', ''''), '','', ''","''), ''"]''),
                 ''$[*]'' COLUMNS (f VARCHAR(64) PATH ''$'')
               ) AS jt2
               WHERE TRIM(jt2.f) <> ''''
             )
           END
         ),
         ''|'',
         GREATEST(IFNULL(c.global_policy_epoch, 0), IFNULL(g.policy_version, 0)), ''|'',
         CASE
           WHEN UPPER(TRIM(g.mode)) IN (''OPTIONAL'', ''REQUIRED'')
             THEN GREATEST(IFNULL(c.global_min_accepted_epoch, 0), IFNULL(g.policy_version, 0))
           ELSE IFNULL(c.global_min_accepted_epoch, 0)
         END
       ), 256)),
     c.legacy_global_merged = b''1''
   WHERE c.id = 1
     AND IFNULL(c.legacy_global_merged, 0) = 0
     AND IFNULL(c.global_policy_epoch, 0) <= IFNULL(g.policy_version, 0)
     -- F-R5-02：仅 seed 或 control 仍为 OFF 且 legacy 为非 OFF（v2 首迁升级）才允许翻译
     AND (
       (
         IFNULL(c.lifecycle_state, '''') = ''UNINITIALIZED''
         AND UPPER(IFNULL(c.global_mode, ''OFF'')) = ''OFF''
         AND IFNULL(c.global_policy_epoch, 0) = 0
         AND IFNULL(c.global_min_accepted_epoch, 0) = 0
         AND c.armed_at IS NULL
       )
       OR (
         UPPER(IFNULL(c.global_mode, ''OFF'')) = ''OFF''
         AND UPPER(TRIM(g.mode)) IN (''OPTIONAL'', ''REQUIRED'')
       )
     )
     -- F-R4-03 / F-R5-02：禁止 mode 降级；禁止同 mode 覆写 factors（control 已是非 OFF 则完全不合并）
     AND UPPER(IFNULL(c.global_mode, ''OFF'')) NOT IN (''OPTIONAL'', ''REQUIRED'')',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 仅纯 seed 行可重算占位 checksum（F-R4-03：禁止对可疑/损坏非 seed 行重新签名洗白）
UPDATE `system_mfa_control_state` c
SET
  c.global_allowed_factors = (
    CASE
      WHEN c.global_allowed_factors IS NULL OR TRIM(c.global_allowed_factors) = '' THEN ''
      ELSE (
        SELECT GROUP_CONCAT(DISTINCT TRIM(jt.f) ORDER BY TRIM(jt.f) SEPARATOR ',')
        FROM JSON_TABLE(
          CONCAT('["', REPLACE(REPLACE(TRIM(c.global_allowed_factors), '"', ''), ',', '","'), '"]'),
          '$[*]' COLUMNS (f VARCHAR(64) PATH '$')
        ) AS jt
        WHERE TRIM(jt.f) <> ''
      )
    END
  ),
  c.checksum = LOWER(SHA2(CONCAT(
    'UNINITIALIZED', '|', 'OFF', '|', '', '|', '0', '|', '0'
  ), 256))
WHERE c.id = 1
  AND IFNULL(c.legacy_global_merged, 0) = 0
  AND IFNULL(c.lifecycle_state, '') = 'UNINITIALIZED'
  AND UPPER(IFNULL(c.global_mode, '')) = 'OFF'
  AND IFNULL(c.global_policy_epoch, 0) = 0
  AND IFNULL(c.global_min_accepted_epoch, 0) = 0
  AND c.armed_at IS NULL
  AND c.checksum IS NOT NULL
  AND c.checksum NOT REGEXP '^[0-9a-f]{64}$';

-- 部署后：不默认插入 REQUIRED；空库仍由应用按 UNINITIALIZED 处理。
-- legacy system_mfa_global_policy 保留只读档案；消费后 legacy_global_merged=1，重放不得回写。
