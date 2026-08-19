-- 部门费用分摊表 + finance_admin 导入权限（幂等）
-- STORY-0012 U1：Excel 列仅期间/部门/金额/备注；sourceType 由表单选择

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `finance_dept_cost_allocation` (
    `id`            bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `period`        varchar(7)    NOT NULL COMMENT '期间 YYYY-MM',
    `source_type`   varchar(32)   NOT NULL COMMENT '来源类型：薪资 / 云服务 / 其他',
    `dept_id`       bigint        NOT NULL COMMENT '部门编号',
    `dept_name`     varchar(255)  NOT NULL COMMENT '部门名称快照',
    `amount`        decimal(18,2) NOT NULL COMMENT '分摊金额',
    `remark`        varchar(512)  DEFAULT NULL COMMENT '备注',
    `importer_id`   bigint        NOT NULL COMMENT '导入人编号',
    `import_time`   datetime      NOT NULL COMMENT '导入时间',
    `creator`       varchar(64)   DEFAULT '' COMMENT '创建者',
    `create_time`   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`       varchar(64)   DEFAULT '' COMMENT '更新者',
    `update_time`   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`     bigint        NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_period_source_tenant` (`period`, `source_type`, `tenant_id`, `deleted`),
    KEY `idx_dept_period` (`dept_id`, `period`)
) ENGINE=InnoDB COMMENT='部门费用分摊';

-- 权限按钮挂在财务目录下（U3 页面落地后再挂到报表页）
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '部门费用分摊导入', 'finance:dept-allocation:import', 3, 90,
       (SELECT `id` FROM `system_menu` WHERE `deleted` = b'0' AND `type` = 1 AND `parent_id` = 0 AND `path` IN ('finance', '/finance') LIMIT 1),
       '', '', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE (SELECT `id` FROM `system_menu` WHERE `deleted` = b'0' AND `type` = 1 AND `parent_id` = 0 AND `path` IN ('finance', '/finance') LIMIT 1) IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `permission` = 'finance:dept-allocation:import'
);

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0' AND r.`code` = 'finance_admin' AND r.`tenant_id` = 1
  AND m.`deleted` = b'0'
  AND m.`permission` = 'finance:dept-allocation:import'
  AND NOT EXISTS (
      SELECT 1 FROM `system_role_menu` rm
      WHERE rm.`role_id` = r.`id` AND rm.`menu_id` = m.`id`
        AND rm.`tenant_id` = 1 AND rm.`deleted` = b'0'
  );
