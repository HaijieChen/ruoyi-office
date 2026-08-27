SET NAMES utf8mb4;

SET @sys := (SELECT id FROM system_menu WHERE deleted=b'0' AND parent_id=0 AND path IN ('/system','system') LIMIT 1);

INSERT INTO system_menu
  (name, permission, type, sort, parent_id, path, icon, component, component_name,
   status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 'MFA 策略', '', 2, 7, @sys, 'mfa-policy', 'ep:lock',
       'system/mfa/policy/index', 'SystemMfaPolicy',
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @sys IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM system_menu m WHERE m.deleted=b'0' AND m.component='system/mfa/policy/index'
  );

INSERT INTO system_menu
  (name, permission, type, sort, parent_id, path, icon, component, component_name,
   status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT btn.name, btn.permission, 3, btn.sort, parent.id, '', '', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM (
    SELECT 'MFA 策略查询' name, 'system:mfa-policy:query' permission, 1 sort
    UNION ALL SELECT 'MFA 策略更新', 'system:mfa-policy:update', 2
) btn
CROSS JOIN (
    SELECT id FROM system_menu WHERE deleted=b'0' AND component='system/mfa/policy/index' LIMIT 1
) parent
WHERE NOT EXISTS (
    SELECT 1 FROM system_menu m WHERE m.deleted=b'0' AND m.permission=btn.permission
);

INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT r.id, m.id, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM system_role r
CROSS JOIN system_menu m
WHERE r.deleted=b'0' AND r.code='super_admin' AND r.tenant_id=1
  AND m.deleted=b'0'
  AND (
        m.component='system/mfa/policy/index'
     OR m.permission IN ('system:mfa-policy:query', 'system:mfa-policy:update')
  )
  AND NOT EXISTS (
      SELECT 1 FROM system_role_menu rm
      WHERE rm.role_id=r.id AND rm.menu_id=m.id AND rm.tenant_id=1 AND rm.deleted=b'0'
  );
