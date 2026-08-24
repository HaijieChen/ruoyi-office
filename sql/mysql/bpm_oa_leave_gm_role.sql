-- 请假超 3 天总经理角色。幂等。
SET NAMES utf8mb4;

INSERT INTO `system_role`
    (`name`, `code`, `sort`, `data_scope`, `data_scope_dept_ids`, `status`, `type`, `remark`,
     `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT '总经理', 'gm', 34, 1, '', 0, 2, 'OA 请假超3天审批',
       'admin', NOW(), 'admin', NOW(), b'0', 1
WHERE NOT EXISTS (
    SELECT 1 FROM `system_role` WHERE `deleted` = b'0' AND `code` = 'gm' AND `tenant_id` = 1
);

-- 测试环境：把已有合同总经理账号也挂上 gm，便于验收
INSERT INTO `system_user_role` (`user_id`, `role_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT ur.`user_id`, gm.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_user_role` ur
JOIN `system_role` cg ON cg.`id` = ur.`role_id` AND cg.`code` = 'contract_gm' AND cg.`deleted` = b'0'
JOIN `system_role` gm ON gm.`code` = 'gm' AND gm.`deleted` = b'0' AND gm.`tenant_id` = 1
WHERE ur.`deleted` = b'0'
  AND NOT EXISTS (
      SELECT 1 FROM `system_user_role` x
      WHERE x.`deleted` = b'0' AND x.`user_id` = ur.`user_id` AND x.`role_id` = gm.`id`
  );
