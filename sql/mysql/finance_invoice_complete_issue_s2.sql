-- S2：整单办票附件子表 finance_invoice_application_file（幂等）
-- 依据 tech-plan D3 / M4：file_url ≥1024；issue_status FULL=2 由应用层写入

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `finance_invoice_application_file` (
    `id`              bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `application_id`  bigint NOT NULL COMMENT '开票申请编号',
    `file_url`        varchar(1024) NOT NULL COMMENT '发票附件 URL',
    `file_name`       varchar(255) DEFAULT NULL COMMENT '文件名',
    `sort`            int NOT NULL DEFAULT 0 COMMENT '排序',
    `creator`         varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time`     datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`         varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time`     datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`       bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_application_id` (`application_id`)
) ENGINE=InnoDB COMMENT='财务开票申请办票附件';
