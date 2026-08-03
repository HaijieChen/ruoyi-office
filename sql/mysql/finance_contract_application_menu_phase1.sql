-- 合同签约申请菜单/权限 + 角色授权（幂等）CS-T6 / CS-F9
-- 组件：finance/contract-application/index
-- processKey = finance_contract_sign
-- 执行权限与 BPM 候选组对齐：
--   record-seal  → contract_seal_admin（用印/归档）
--   record-mail  → contract_mail（邮寄）
--   update       → finance_admin 仅 manageAll 查询，不作为执行入口

SET NAMES utf8mb4;

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '合同签约', '', 2, 34,
       (SELECT `id` FROM `system_menu` WHERE `deleted` = b'0' AND `type` = 1 AND `parent_id` = 0 AND `path` IN ('finance', '/finance') LIMIT 1),
       'contract-application', 'ep:notebook', 'finance/contract-application/index', 'FinanceContractApplication',
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'finance/contract-application/index'
);

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT btn.name, btn.permission, 3, btn.sort, parent.id, '', '', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM (
    SELECT '合同签约查询' AS name, 'finance:contract-application:query' AS permission, 10 AS sort
    UNION ALL SELECT '合同签约提交', 'finance:contract-application:create', 11
    UNION ALL SELECT '合同签约重提', 'finance:contract-application:resubmit', 12
    UNION ALL SELECT '合同签约更新', 'finance:contract-application:update', 13
    UNION ALL SELECT '合同用印归档', 'finance:contract-application:record-seal', 14
    UNION ALL SELECT '合同邮寄登记', 'finance:contract-application:record-mail', 15
) btn
CROSS JOIN (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'finance/contract-application/index' LIMIT 1
) parent
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu` m
    WHERE m.`deleted` = b'0' AND m.`permission` = btn.permission
);

-- BPM 角色（code 与 candidateParam 一致，CS-F12 ROLE strategy=10 按 code 解析）
INSERT INTO `system_role`
    (`name`, `code`, `sort`, `data_scope`, `data_scope_dept_ids`, `status`, `type`, `remark`,
     `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.name, r.code, r.sort, 1, '', 0, 2, r.remark,
       'admin', NOW(), 'admin', NOW(), b'0', 1
FROM (
    SELECT '合同业务主管' AS name, 'contract_biz_lead' AS code, 35 AS sort, 'BPM finance_contract_sign taskBizLead' AS remark
    UNION ALL SELECT '合同法务', 'contract_legal', 36, 'BPM finance_contract_sign taskLegal'
    UNION ALL SELECT '合同财务审批', 'contract_finance', 37, 'BPM finance_contract_sign taskFinance'
    UNION ALL SELECT '合同总经理', 'contract_gm', 38, 'BPM finance_contract_sign taskGm'
    UNION ALL SELECT '合同用印管理员', 'contract_seal_admin', 40, 'BPM finance_contract_sign taskSeal/taskArchive'
    UNION ALL SELECT '合同邮寄执行', 'contract_mail', 41, 'BPM finance_contract_sign taskMail'
) r
WHERE NOT EXISTS (
    SELECT 1 FROM `system_role` sr WHERE sr.`deleted` = b'0' AND sr.`code` = r.code AND sr.`tenant_id` = 1
);

-- business_staff：发起/查询/重提
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0' AND r.`code` = 'business_staff' AND r.`tenant_id` = 1
  AND m.`deleted` = b'0'
  AND (
        m.`component` = 'finance/contract-application/index'
     OR m.`permission` IN (
            'finance:contract-application:query',
            'finance:contract-application:create',
            'finance:contract-application:resubmit'
        )
  )
  AND NOT EXISTS (
      SELECT 1 FROM `system_role_menu` rm
      WHERE rm.`role_id` = r.`id` AND rm.`menu_id` = m.`id` AND rm.`tenant_id` = 1 AND rm.`deleted` = b'0'
  );

-- finance_admin：查询 + update（manageAll 全量，非执行入口）
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0' AND r.`code` = 'finance_admin' AND r.`tenant_id` = 1
  AND m.`deleted` = b'0'
  AND (
        m.`component` = 'finance/contract-application/index'
     OR m.`permission` IN (
            'finance:contract-application:query',
            'finance:contract-application:update'
        )
  )
  AND NOT EXISTS (
      SELECT 1 FROM `system_role_menu` rm
      WHERE rm.`role_id` = r.`id` AND rm.`menu_id` = m.`id` AND rm.`tenant_id` = 1 AND rm.`deleted` = b'0'
  );

-- contract_seal_admin：用印/归档执行
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0' AND r.`code` = 'contract_seal_admin' AND r.`tenant_id` = 1
  AND m.`deleted` = b'0'
  AND m.`permission` IN (
        'finance:contract-application:query',
        'finance:contract-application:record-seal'
  )
  AND NOT EXISTS (
      SELECT 1 FROM `system_role_menu` rm
      WHERE rm.`role_id` = r.`id` AND rm.`menu_id` = m.`id` AND rm.`tenant_id` = 1 AND rm.`deleted` = b'0'
  );

-- contract_mail：邮寄执行
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0' AND r.`code` = 'contract_mail' AND r.`tenant_id` = 1
  AND m.`deleted` = b'0'
  AND m.`permission` IN (
        'finance:contract-application:query',
        'finance:contract-application:record-mail'
  )
  AND NOT EXISTS (
      SELECT 1 FROM `system_role_menu` rm
      WHERE rm.`role_id` = r.`id` AND rm.`menu_id` = m.`id` AND rm.`tenant_id` = 1 AND rm.`deleted` = b'0'
  );

-- 审批四角色：仅 query，供待办打开详情（C29 / CS-R1）；不授予 update/列表全量
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0' AND r.`tenant_id` = 1
  AND r.`code` IN ('contract_biz_lead', 'contract_legal', 'contract_finance', 'contract_gm')
  AND m.`deleted` = b'0'
  AND m.`permission` = 'finance:contract-application:query'
  AND NOT EXISTS (
      SELECT 1 FROM `system_role_menu` rm
      WHERE rm.`role_id` = r.`id` AND rm.`menu_id` = m.`id` AND rm.`tenant_id` = 1 AND rm.`deleted` = b'0'
  );
