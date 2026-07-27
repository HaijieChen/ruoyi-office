-- 修复到款认领 / 到款认领复核菜单图标
-- 原因：Iconify 的 fa: 前缀对应 Font Awesome 4，无 fa:handshake、fa:check-double（FA5 图标），
-- 侧栏 VbenIcon 无法渲染，表现为「没有 label 图标」。
-- 改为 FA4 可用图标：hand-o-up、check-circle。

UPDATE `system_menu`
SET `icon` = 'fa:hand-o-up',
    `update_time` = NOW()
WHERE `deleted` = b'0'
  AND `component` = 'finance/receipt-claim/index'
  AND (`icon` IS NULL OR `icon` = '' OR `icon` IN ('fa:handshake', 'fa:handshake-o'));

UPDATE `system_menu`
SET `icon` = 'fa:check-circle',
    `update_time` = NOW()
WHERE `deleted` = b'0'
  AND `component` = 'finance/receipt-claim/review'
  AND (`icon` IS NULL OR `icon` = '' OR `icon` IN ('fa:check-double', 'fa:check-double-o'));
