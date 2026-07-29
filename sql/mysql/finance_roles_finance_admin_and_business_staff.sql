-- 财务业务角色：财务管理员 + 商务人员（幂等）
-- tenant_id 默认 1（与本地 super_admin 一致）

-- ========== 1. 创建角色 ==========
INSERT INTO `system_role`
    (`name`, `code`, `sort`, `data_scope`, `data_scope_dept_ids`, `status`, `type`, `remark`,
     `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT '财务管理员', 'finance_admin', 20, 1, '', 0, 2,
       '财务全量权限：银行到款/商务单 CRUD 与导入、到款认领复核（确认/驳回/撤销）及审计',
       'admin', NOW(), 'admin', NOW(), b'0', 1
WHERE NOT EXISTS (
    SELECT 1 FROM `system_role` WHERE `deleted` = b'0' AND `code` = 'finance_admin' AND `tenant_id` = 1
);

INSERT INTO `system_role`
    (`name`, `code`, `sort`, `data_scope`, `data_scope_dept_ids`, `status`, `type`, `remark`,
     `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT '商务人员', 'business_staff', 30, 1, '', 0, 2,
       '商务侧权限：查看到款、维护商务单、发起/修改/重提认领（不含财务复核）',
       'admin', NOW(), 'admin', NOW(), b'0', 1
WHERE NOT EXISTS (
    SELECT 1 FROM `system_role` WHERE `deleted` = b'0' AND `code` = 'business_staff' AND `tenant_id` = 1
);

UPDATE `system_role`
SET `name` = '财务管理员',
    `sort` = 20,
    `status` = 0,
    `remark` = '财务全量权限：银行到款/商务单 CRUD 与导入、到款认领复核（确认/驳回/撤销）及审计',
    `updater` = 'admin',
    `update_time` = NOW()
WHERE `deleted` = b'0' AND `code` = 'finance_admin' AND `tenant_id` = 1;

UPDATE `system_role`
SET `name` = '商务人员',
    `sort` = 30,
    `status` = 0,
    `remark` = '商务侧权限：查看到款、维护商务单、发起/修改/重提认领（不含财务复核）',
    `updater` = 'admin',
    `update_time` = NOW()
WHERE `deleted` = b'0' AND `code` = 'business_staff' AND `tenant_id` = 1;

-- ========== 2. 财务管理员：财务模块全部菜单/按钮 ==========
-- 先清后插，保证权限集合可重复执行收敛
DELETE FROM `system_role_menu`
WHERE `tenant_id` = 1
  AND `role_id` = (SELECT `id` FROM `system_role` WHERE `deleted` = b'0' AND `code` = 'finance_admin' AND `tenant_id` = 1 LIMIT 1);

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0' AND r.`code` = 'finance_admin' AND r.`tenant_id` = 1
  AND m.`deleted` = b'0'
  AND (
        (m.`parent_id` = 0 AND m.`path` = '/finance')
     OR m.`component` LIKE 'finance/%'
     OR m.`permission` LIKE 'finance:%'
     OR m.`parent_id` IN (
            SELECT p.`id` FROM `system_menu` p
            WHERE p.`deleted` = b'0'
              AND (
                    (p.`parent_id` = 0 AND p.`path` = '/finance')
                 OR p.`component` LIKE 'finance/%'
              )
        )
  );

-- ========== 3. 商务人员：到款只读 + 商务单维护 + 认领提交侧 ==========
DELETE FROM `system_role_menu`
WHERE `tenant_id` = 1
  AND `role_id` = (SELECT `id` FROM `system_role` WHERE `deleted` = b'0' AND `code` = 'business_staff' AND `tenant_id` = 1 LIMIT 1);

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0' AND r.`code` = 'business_staff' AND r.`tenant_id` = 1
  AND m.`deleted` = b'0'
  AND (
        -- 目录/页面
        (m.`parent_id` = 0 AND m.`path` = '/finance')
     OR m.`component` IN (
            'finance/receipt/index',
            'finance/business-order/index',
            'finance/receipt-claim/index'
        )
        -- 银行到款：仅查询
     OR m.`permission` IN ('finance:receipt:query')
        -- 商务单：查询/创建/更新/删除/导入
     OR m.`permission` IN (
            'finance:business-order:query',
            'finance:business-order:create',
            'finance:business-order:update',
            'finance:business-order:delete',
            'finance:business-order:import'
        )
        -- 认领：本人侧（不含复核/确认/驳回/撤销）
     OR m.`permission` IN (
            'finance:receipt-claim:query',
            'finance:receipt-claim:create',
            'finance:receipt-claim:update',
            'finance:receipt-claim:resubmit'
        )
  );
