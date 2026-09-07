-- OA 加班/补卡额度稳定行锁（幂等）。KTD13：事务内 ODKU 拿 X 锁，提交/回滚释放。
-- 无逻辑删除。UK = (tenant_id, user_id, quota_type, period)

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `bpm_oa_quota_lock` (
    `tenant_id`   bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    `user_id`     bigint NOT NULL COMMENT '申请人用户编号',
    `quota_type`  varchar(32) NOT NULL COMMENT 'OVERTIME_DAY | PUNCH_MONTH',
    `period`      varchar(16) NOT NULL COMMENT '自然日 yyyy-MM-dd 或自然月 yyyy-MM',
    `creator`     varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`tenant_id`, `user_id`, `quota_type`, `period`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OA加班/补卡额度事务行锁';
