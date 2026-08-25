-- 用印申请详情（隐藏页）：列表「新增」和统一发起都走这条路由
SET NAMES utf8mb4;

SET @legal := (
  SELECT id FROM `system_menu`
  WHERE `deleted` = b'0' AND `path` = 'legal'
    AND `parent_id` IN (
      SELECT id FROM `system_menu` WHERE `deleted` = b'0' AND `parent_id` = 0 AND `path` IN ('finance', '/finance')
    )
  LIMIT 1
);

INSERT INTO `system_menu` (
  `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
  `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`
)
SELECT '用印申请详情', '', 2, 94, @legal, '/oa/seal/seal-apply-info', '',
       'oa/seal/sealapply/info/index', 'OaSealApplyBillInfo',
       0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @legal IS NOT NULL
  AND NOT EXISTS (
        SELECT 1 FROM `system_menu`
        WHERE `deleted` = b'0' AND `component` = 'oa/seal/sealapply/info/index'
    );
