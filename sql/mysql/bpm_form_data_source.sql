-- ----------------------------
-- BPM 表单数据源定义表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `bpm_form_data_source` (
    `id`                bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `name`              varchar(63) NOT NULL COMMENT '数据源名称',
    `code`              varchar(127) NOT NULL COMMENT '数据源标识',
    `type`              tinyint NOT NULL COMMENT '数据源类型（1-SQL, 2-DICT, 3-PLATFORM_API）',
    `status`            tinyint NOT NULL COMMENT '状态（0-开启, 1-关闭）',
    `published_version` int DEFAULT NULL COMMENT '已发布版本号',
    `creator`           varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time`       datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`           varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time`       datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`         bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code_tenant_deleted` (`code`, `tenant_id`, `deleted`)
) ENGINE=InnoDB COMMENT='BPM 表单数据源定义';

-- ----------------------------
-- BPM 表单数据源版本表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `bpm_form_data_source_version` (
    `id`                bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `data_source_id`    bigint NOT NULL COMMENT '数据源编号',
    `version`           int NOT NULL COMMENT '版本号',
    `status`            tinyint NOT NULL COMMENT '状态',
    `source_config`     text NOT NULL COMMENT '数据源配置（JSON）',
    `parameter_schema`  text COMMENT '参数 schema（JSON）',
    `result_schema`     text COMMENT '结果 schema（JSON）',
    `label_field`       varchar(63) DEFAULT NULL COMMENT '标签字段名',
    `value_field`       varchar(63) DEFAULT NULL COMMENT '值字段名',
    `pageable`          bit(1) NOT NULL DEFAULT b'0' COMMENT '是否分页',
    `max_rows`          int DEFAULT NULL COMMENT '最大行数',
    `timeout_seconds`   int DEFAULT NULL COMMENT '超时秒数',
    `cache_seconds`     int DEFAULT NULL COMMENT '缓存秒数',
    `creator`           varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time`       datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`           varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time`       datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`         bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ds_version_deleted` (`data_source_id`, `version`, `deleted`)
) ENGINE=InnoDB COMMENT='BPM 表单数据源版本';

-- ----------------------------
-- BPM 表单数据源调用日志表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `bpm_form_data_source_log` (
    `id`                  bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `data_source_id`      bigint NOT NULL COMMENT '数据源编号',
    `version`             int NOT NULL COMMENT '数据源版本号',
    `form_id`             bigint DEFAULT NULL COMMENT '表单编号',
    `process_instance_id` varchar(64) DEFAULT NULL COMMENT '流程实例编号',
    `user_id`             bigint DEFAULT NULL COMMENT '用户编号',
    `parameter_digest`    varchar(255) DEFAULT NULL COMMENT '参数摘要',
    `row_count`           int DEFAULT NULL COMMENT '返回行数',
    `duration_ms`         bigint DEFAULT NULL COMMENT '执行耗时（毫秒）',
    `success`             bit(1) NOT NULL COMMENT '是否成功',
    `error_code`          varchar(127) DEFAULT NULL COMMENT '错误码',
    `creator`             varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time`         datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`             varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time`         datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`             bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`           bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_ds_create_time` (`data_source_id`, `create_time`)
) ENGINE=InnoDB COMMENT='BPM 表单数据源调用日志';

-- ----------------------------
-- BPM 表单数据源菜单（可重复执行）
-- ----------------------------
-- 优先匹配生产环境的“流程设置”，兼容上游初始化脚本中的旧名称“流程管理”。
-- parent_id 和子菜单 id 均通过稳定业务字段查询，不依赖固定数字编号。
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '表单数据源', '', 2, 7, parent_menu.id, 'form-data-source', 'fa:database',
       'bpm/form-data-source/index', 'BpmFormDataSource', 0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
FROM (
    SELECT `id`
    FROM `system_menu`
    WHERE `deleted` = b'0'
      AND `type` = 1
      AND `name` IN ('流程设置', '流程管理')
    ORDER BY CASE WHEN `name` = '流程设置' THEN 0 ELSE 1 END
    LIMIT 1
) parent_menu
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'bpm/form-data-source/index'
);

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '表单数据源查询', 'bpm:form-data-source:query', 3, 1, page_menu.id, '', '', '', NULL,
       0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
FROM (SELECT `id` FROM `system_menu`
      WHERE `deleted` = b'0' AND `component` = 'bpm/form-data-source/index' LIMIT 1) page_menu
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `permission` = 'bpm:form-data-source:query'
);

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '表单数据源创建', 'bpm:form-data-source:create', 3, 2, page_menu.id, '', '', '', NULL,
       0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
FROM (SELECT `id` FROM `system_menu`
      WHERE `deleted` = b'0' AND `component` = 'bpm/form-data-source/index' LIMIT 1) page_menu
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `permission` = 'bpm:form-data-source:create'
);

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '表单数据源更新', 'bpm:form-data-source:update', 3, 3, page_menu.id, '', '', '', NULL,
       0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
FROM (SELECT `id` FROM `system_menu`
      WHERE `deleted` = b'0' AND `component` = 'bpm/form-data-source/index' LIMIT 1) page_menu
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `permission` = 'bpm:form-data-source:update'
);

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '表单数据源发布', 'bpm:form-data-source:publish', 3, 4, page_menu.id, '', '', '', NULL,
       0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
FROM (SELECT `id` FROM `system_menu`
      WHERE `deleted` = b'0' AND `component` = 'bpm/form-data-source/index' LIMIT 1) page_menu
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `permission` = 'bpm:form-data-source:publish'
);

-- 将新页面及按钮权限授予当前租户的超级管理员角色。
-- 使用角色 code、组件和权限标识定位，避免依赖环境相关的自增编号。
INSERT INTO `system_role_menu`
    (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT role_record.id, menu_record.id, '', NOW(), '', NOW(), b'0', role_record.tenant_id
FROM `system_role` role_record
JOIN `system_menu` menu_record
  ON menu_record.`deleted` = b'0'
 AND (menu_record.`component` = 'bpm/form-data-source/index'
      OR menu_record.`permission` LIKE 'bpm:form-data-source:%')
WHERE role_record.`deleted` = b'0'
  AND role_record.`status` = 0
  AND role_record.`code` = 'super_admin'
  AND NOT EXISTS (
      SELECT 1
      FROM `system_role_menu` role_menu
      WHERE role_menu.`deleted` = b'0'
        AND role_menu.`role_id` = role_record.id
        AND role_menu.`menu_id` = menu_record.id
  );
