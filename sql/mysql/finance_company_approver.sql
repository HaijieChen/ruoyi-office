-- 主体公司财务审批人映射 + 财务菜单
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `finance_company_approver` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `entity_company_dept_id` bigint NOT NULL COMMENT '主体公司 system_dept.id',
  `user_id` bigint NOT NULL COMMENT '财务审批人',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_company` (`entity_company_dept_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='主体公司财务审批人';

SET @mdm := (
  SELECT id FROM `system_menu`
  WHERE `deleted` = b'0' AND `id` = 5278
  LIMIT 1
);

INSERT INTO `system_menu` (
  `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
  `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`
)
SELECT '公司财务审批人', '', 2, 20, @mdm, 'company-approver', 'ep:user',
       'finance/company-approver/index', 'FinanceCompanyApprover',
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @mdm IS NOT NULL
  AND NOT EXISTS (
        SELECT 1 FROM `system_menu`
        WHERE `deleted` = b'0' AND `component` = 'finance/company-approver/index'
    );

SET @page := (
  SELECT id FROM `system_menu`
  WHERE `deleted` = b'0' AND `component` = 'finance/company-approver/index'
  LIMIT 1
);

INSERT INTO `system_menu` (
  `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
  `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`
)
SELECT t.name, t.permission, 3, t.sort, @page, '', '', '', NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM (
  SELECT '公司财务审批人查询' name, 'finance:company-approver:query' permission, 1 sort
  UNION ALL SELECT '公司财务审批人更新', 'finance:company-approver:update', 2
) t
WHERE @page IS NOT NULL
  AND NOT EXISTS (
        SELECT 1 FROM `system_menu`
        WHERE `deleted` = b'0' AND `permission` = t.permission
    );

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.id, m.id, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM system_role r
CROSS JOIN system_menu m
WHERE r.deleted=b'0' AND r.tenant_id=1 AND r.code IN ('finance_admin', 'super_admin')
  AND m.deleted=b'0'
  AND (
        m.component = 'finance/company-approver/index'
     OR m.permission IN ('finance:company-approver:query', 'finance:company-approver:update')
  )
  AND NOT EXISTS (
      SELECT 1 FROM system_role_menu rm
      WHERE rm.role_id=r.id AND rm.menu_id=m.id AND rm.tenant_id=1 AND rm.deleted=b'0'
  );
