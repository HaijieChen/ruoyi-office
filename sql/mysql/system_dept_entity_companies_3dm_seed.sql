-- 组织架构：补充 3DM 关联主体公司（org_type=1 公司）
-- 幂等：按 name + tenant_id + deleted 判重；parent_id=0 顶级公司节点
-- 供财务主体公司 / 开票公司下拉 getSimpleCompanyList 使用

SET NAMES utf8mb4;

-- 上海文枢网络科技有限公司
INSERT INTO `system_dept` (
    `name`, `parent_id`, `sort`, `leader_user_id`, `phone`, `email`, `status`, `org_type`,
    `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`
)
SELECT '上海文枢网络科技有限公司', 0, 2, NULL, '', '', 0, '1',
       '1', NOW(), '1', NOW(), b'0', 1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dept`
    WHERE `name` = '上海文枢网络科技有限公司' AND `tenant_id` = 1 AND `deleted` = b'0'
);

-- 卡饭（上海）信息安全有限公司
INSERT INTO `system_dept` (
    `name`, `parent_id`, `sort`, `leader_user_id`, `phone`, `email`, `status`, `org_type`,
    `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`
)
SELECT '卡饭（上海）信息安全有限公司', 0, 3, NULL, '', '', 0, '1',
       '1', NOW(), '1', NOW(), b'0', 1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dept`
    WHERE `name` = '卡饭（上海）信息安全有限公司' AND `tenant_id` = 1 AND `deleted` = b'0'
);

-- 北京三鼎梦软件服务有限公司
INSERT INTO `system_dept` (
    `name`, `parent_id`, `sort`, `leader_user_id`, `phone`, `email`, `status`, `org_type`,
    `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`
)
SELECT '北京三鼎梦软件服务有限公司', 0, 4, NULL, '', '', 0, '1',
       '1', NOW(), '1', NOW(), b'0', 1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dept`
    WHERE `name` = '北京三鼎梦软件服务有限公司' AND `tenant_id` = 1 AND `deleted` = b'0'
);

-- 确保已存在的同名节点为启用公司（防止历史误建为部门）
UPDATE `system_dept`
SET `org_type` = '1',
    `status` = 0,
    `updater` = '1',
    `update_time` = NOW()
WHERE `tenant_id` = 1
  AND `deleted` = b'0'
  AND `name` IN (
      '上海文枢网络科技有限公司',
      '卡饭（上海）信息安全有限公司',
      '北京三鼎梦软件服务有限公司'
  );
