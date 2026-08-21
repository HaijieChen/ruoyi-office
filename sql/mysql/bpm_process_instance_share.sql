-- 流程实例分享（发起人把已通过实例分享给指定用户）
-- 幂等：可重复执行

CREATE TABLE IF NOT EXISTS `bpm_process_instance_share` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `process_instance_id` varchar(64) NOT NULL COMMENT '流程实例编号',
  `start_user_id` bigint NOT NULL COMMENT '发起人用户编号',
  `recipient_user_id` bigint NOT NULL COMMENT '被分享用户编号',
  `process_instance_name` varchar(255) DEFAULT '' COMMENT '流程名冗余',
  `revoked_at` datetime DEFAULT NULL COMMENT '收回时间，空表示有效',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_pi_recipient` (`process_instance_id`, `recipient_user_id`, `tenant_id`, `deleted`) USING BTREE,
  KEY `idx_recipient_active` (`recipient_user_id`, `revoked_at`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程实例分享';
