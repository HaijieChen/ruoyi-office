-- 财务/商务角色补用印申请单权限（幂等）
SET NAMES utf8mb4;

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.id, m.id, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM system_role r
CROSS JOIN system_menu m
WHERE r.deleted=b'0' AND r.tenant_id=1 AND r.code IN ('finance_admin', 'business_staff')
  AND m.deleted=b'0'
  AND (
        m.permission IN (
          'oa:seal-apply-bill:query',
          'oa:seal-apply-bill:create',
          'oa:seal-apply-bill:update',
          'oa:seal-apply-bill:submit',
          'oa:seal-apply-bill:withdraw',
          'oa:seal:query'
        )
     OR m.component IN (
          'oa/seal/sealapply/list/index',
          'oa/seal/sealapply/info/index'
        )
     OR m.path IN ('/finance/seal-apply', '/oa/seal/seal-apply-info')
  )
  AND NOT EXISTS (
      SELECT 1 FROM system_role_menu rm
      WHERE rm.role_id=r.id AND rm.menu_id=m.id AND rm.tenant_id=1 AND rm.deleted=b'0'
  );
