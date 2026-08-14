-- ADR-MFA-v3 切片 1：契约与权威模型
-- 可重复执行；默认不写入非 OFF 策略（UNINITIALIZED/OFF 安全默认）
-- 替代 v2 旁路表结构（global 策略并入 control_state）

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
