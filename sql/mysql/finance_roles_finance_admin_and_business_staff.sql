-- 财务业务角色：财务管理员 + 商务人员（幂等，tenant_id=1）
-- 职责分离（SoD）矩阵（2026-07-29 与 GPT 评审对齐）：
--   finance_admin：银行到款全量；商务单只读；认领仅复核（confirm/reject/revoke/review）；组织架构(dept CRUD)
--   business_staff：银行到款只读；商务单维护；认领提交侧（create/update/resubmit/query）
-- 双方均含工作台 /dashboard + /workspace + /home，避免 defaultHomePath=/home 登录 404。
-- 可配首页 component=dashboard/home/index；旧 workspace 保留兼容（redirect→/home）。
-- 注意：不要用「全部 finance:%」回灌 FA，会破坏 SoD。

-- ========== 1. 创建/更新角色 ==========
INSERT INTO `system_role`
    (`name`, `code`, `sort`, `data_scope`, `data_scope_dept_ids`, `status`, `type`, `remark`,
     `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT '财务管理员', 'finance_admin', 20, 1, '', 0, 2,
       '财务职责：银行到款全量维护；商务单只读；到款认领仅复核；组织架构维护主体公司。不含商务单写入与认领发起。',
       'admin', NOW(), 'admin', NOW(), b'0', 1
WHERE NOT EXISTS (
    SELECT 1 FROM `system_role` WHERE `deleted` = b'0' AND `code` = 'finance_admin' AND `tenant_id` = 1
);

INSERT INTO `system_role`
    (`name`, `code`, `sort`, `data_scope`, `data_scope_dept_ids`, `status`, `type`, `remark`,
     `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT '商务人员', 'business_staff', 30, 1, '', 0, 2,
       '商务职责：银行到款只读；商务单维护；发起/修改/重提认领。不含财务复核与到款写入。',
       'admin', NOW(), 'admin', NOW(), b'0', 1
WHERE NOT EXISTS (
    SELECT 1 FROM `system_role` WHERE `deleted` = b'0' AND `code` = 'business_staff' AND `tenant_id` = 1
);

UPDATE `system_role`
SET `name` = '财务管理员',
    `sort` = 20,
    `status` = 0,
    `remark` = '财务职责：银行到款全量维护；商务单只读；到款认领仅复核；组织架构维护主体公司。不含商务单写入与认领发起。',
    `updater` = 'admin',
    `update_time` = NOW()
WHERE `deleted` = b'0' AND `code` = 'finance_admin' AND `tenant_id` = 1;

UPDATE `system_role`
SET `name` = '商务人员',
    `sort` = 30,
    `status` = 0,
    `remark` = '商务职责：银行到款只读；商务单维护；发起/修改/重提认领。不含财务复核与到款写入。',
    `updater` = 'admin',
    `update_time` = NOW()
WHERE `deleted` = b'0' AND `code` = 'business_staff' AND `tenant_id` = 1;

-- ========== 2. 财务管理员：SoD 白名单（先清后插，tenant_id 必须为 1） ==========
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
        -- 工作台（目录 + 可配首页 + 旧 workspace 兼容）
        (m.`parent_id` = 0 AND m.`path` = '/dashboard')
     OR m.`component` = 'dashboard/home/index'
     OR m.`component` = 'dashboard/workspace/index'
        -- 财务管理目录
     OR (m.`parent_id` = 0 AND m.`path` = '/finance')
        -- 银行到款页 + 全量按钮
     OR m.`component` = 'finance/receipt/index'
     OR m.`permission` IN (
            'finance:receipt:query',
            'finance:receipt:create',
            'finance:receipt:update',
            'finance:receipt:delete',
            'finance:receipt:import',
            'finance:receipt:close',
            'finance:receipt:reopen',
            'finance:receipt:audit-query'
        )
        -- 商务单页 + 仅查询
     OR m.`component` = 'finance/business-order/index'
     OR m.`permission` = 'finance:business-order:query'
        -- 认领复核页 + 复核动作（不含提交侧）
     OR m.`component` = 'finance/receipt-claim/review'
     OR m.`permission` IN (
            'finance:receipt-claim:review',
            'finance:receipt-claim:confirm',
            'finance:receipt-claim:reject',
            'finance:receipt-claim:revoke'
        )
        -- 工作流程 / 审批中心（待办办理、已办、我的流程、抄送、发起）
     OR (m.`parent_id` = 0 AND m.`path` = '/bpm')
     OR m.`id` IN (
            1200,  -- 审批中心
            1201,  -- 我的流程
            1207,  -- 待办任务
            1208,  -- 已办任务
            2713,  -- 抄送我的
            2720   -- 发起流程
        )
     OR m.`permission` IN (
            'bpm:process-instance:query',
            'bpm:process-instance:create',
            'bpm:process-instance:cancel',
            'bpm:process-instance-cc:query',
            'bpm:task:query',
            'bpm:task:update'
        )
        -- 组织架构（人力 → 组织架构 / 系统管理 → 部门管理）+ dept API
     OR m.`id` IN (103, 5130, 5131, 5132, 5133)
     OR m.`permission` IN (
            'system:dept:query',
            'system:dept:create',
            'system:dept:update',
            'system:dept:delete'
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
        -- 工作台（目录 + 可配首页 + 旧 workspace 兼容）
        (m.`parent_id` = 0 AND m.`path` = '/dashboard')
     OR m.`component` = 'dashboard/home/index'
     OR m.`component` = 'dashboard/workspace/index'
        -- 目录/页面
     OR (m.`parent_id` = 0 AND m.`path` = '/finance')
     OR m.`component` IN (
            'finance/receipt/index',
            'finance/business-order/index',
            'finance/receipt-claim/index'
        )
        -- 银行到款：仅查询
     OR m.`permission` = 'finance:receipt:query'
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
        -- 工作流程 / 审批中心（与 FA 同一套业务办理能力，不含流程管理配置）
     OR (m.`parent_id` = 0 AND m.`path` = '/bpm')
     OR m.`id` IN (
            1200,  -- 审批中心
            1201,  -- 我的流程
            1207,  -- 待办任务
            1208,  -- 已办任务
            2713,  -- 抄送我的
            2720   -- 发起流程
        )
     OR m.`permission` IN (
            'bpm:process-instance:query',
            'bpm:process-instance:create',
            'bpm:process-instance:cancel',
            'bpm:process-instance-cc:query',
            'bpm:task:query',
            'bpm:task:update'
        )
  );
