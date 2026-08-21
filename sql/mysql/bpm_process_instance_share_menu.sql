-- 分享给我的：抄送我的兄弟菜单。幂等按 path+parent 判断。
INSERT INTO system_menu (
  name, permission, type, sort, parent_id, path, icon, component, component_name,
  status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted
)
SELECT
  '分享给我的', 'bpm:process-instance:query', 2, 31, 1200, 'shared-with-me',
  'ep:share', 'bpm/task/share/index', 'BpmProcessInstanceShare',
  0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM system_menu WHERE deleted = 0 AND parent_id = 1200 AND path = 'shared-with-me'
);
