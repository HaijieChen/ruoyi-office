-- OA 补卡申请表（幂等可重复执行）

SET NAMES utf8mb4;

-- ----------------------------
-- Table: bpm_oa_punch_correction
-- ----------------------------
CREATE TABLE IF NOT EXISTS `bpm_oa_punch_correction` (
    `id`                     bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `user_id`                bigint NOT NULL COMMENT '申请人的用户编号',
    `punch_date`             date NOT NULL COMMENT '补卡日期（额度按该日所在月计次）',
    `punch_time`             datetime NOT NULL COMMENT '补卡时间（日期+时刻，可与补卡日期不同）',
    `reason`                 varchar(500) NOT NULL COMMENT '补卡事由',
    `attachment_urls`        json DEFAULT NULL COMMENT '说明附件 URL 数组',
    `status`                 tinyint NOT NULL COMMENT '审批结果',
    `process_instance_id`    varchar(64) DEFAULT NULL COMMENT '流程实例的编号',
    `attendance_sync_status` varchar(32) NOT NULL DEFAULT 'NOT_SYNCED' COMMENT '考勤同步状态：NOT_SYNCED/SYNCED/FAILED',
    `creator`                varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time`            datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`                varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time`            datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`              bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_user_punch_date` (`user_id`, `punch_date`)
) ENGINE=InnoDB COMMENT='OA 补卡申请';
