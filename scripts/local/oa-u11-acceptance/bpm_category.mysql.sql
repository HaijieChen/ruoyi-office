-- MySQL translation of yudao-module-bpm-server/src/test/resources/sql/create_tables.sql bpm_category
-- plus tenant_id required by sql/mysql/bpm_process_start_catalog.sql INSERT list.
-- Empty table only; category rows come from the catalog incremental SQL.

CREATE TABLE IF NOT EXISTS `bpm_category` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分类编号',
    `name` varchar(63) NOT NULL COMMENT '分类名',
    `code` varchar(63) NOT NULL COMMENT '分类标志',
    `description` varchar(255) NOT NULL COMMENT '分类描述',
    `status` tinyint NOT NULL COMMENT '分类状态',
    `sort` int NOT NULL COMMENT '分类排序',
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB COMMENT='BPM 流程分类';
