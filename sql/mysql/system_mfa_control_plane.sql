-- MFA 控制面 + 类型化策略（ADR-MFA-v2 ①）
-- 默认不写入任何非 OFF 策略；空库视为 UNINITIALIZED/OFF

CREATE TABLE IF NOT EXISTS `system_mfa_control_state` (
  `id` bigint NOT NULL COMMENT '固定为 1 的单例',
  `lifecycle_state` varchar(32) NOT NULL DEFAULT 'UNINITIALIZED' COMMENT 'UNINITIALIZED/ARMED/DEGRADED_CLOSED',
  `policy_version` bigint NOT NULL DEFAULT 0 COMMENT '主库版本门闩，单调递增',
  `armed_at` datetime NULL DEFAULT NULL COMMENT '首次 ARMED 时间',
  `checksum` varchar(128) NULL DEFAULT NULL COMMENT '与当前生效策略对齐的 checksum',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MFA 控制面单例状态';

CREATE TABLE IF NOT EXISTS `system_mfa_global_policy` (
  `id` bigint NOT NULL COMMENT '固定为 1',
  `mode` varchar(32) NOT NULL COMMENT 'OFF/OPTIONAL/REQUIRED',
  `allowed_factors` varchar(255) NULL DEFAULT NULL COMMENT '逗号分隔因子',
  `policy_version` bigint NOT NULL DEFAULT 0,
  `checksum` varchar(128) NOT NULL,
  `confirmed` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否已确认生效',
  `creator` varchar(64) NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全局 MFA 策略';

CREATE TABLE IF NOT EXISTS `system_mfa_tenant_policy` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL COMMENT '租户编号',
  `mode` varchar(32) NOT NULL COMMENT 'INHERIT/OFF/OPTIONAL/REQUIRED',
  `allowed_factors` varchar(255) NULL DEFAULT NULL,
  `policy_version` bigint NOT NULL DEFAULT 0,
  `checksum` varchar(128) NOT NULL,
  `confirmed` bit(1) NOT NULL DEFAULT b'0',
  `creator` varchar(64) NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户 MFA 策略';
