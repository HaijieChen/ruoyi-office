-- 将认领复核动作按钮挂到「到款认领复核」页面下（原挂在「到款认领」提交页下）
-- 背景：filterDisableMenus 会在父菜单未授权时过滤子按钮，导致 FA 仅授复核页时
-- 拿不到 review/confirm/reject/revoke 权限标识。
-- 幂等：可重复执行。

UPDATE `system_menu`
SET `parent_id` = (
        SELECT id FROM (
            SELECT `id` FROM `system_menu`
            WHERE `deleted` = b'0' AND `component` = 'finance/receipt-claim/review'
            LIMIT 1
        ) t
    ),
    `updater` = 'admin',
    `update_time` = NOW()
WHERE `deleted` = b'0'
  AND `permission` IN (
        'finance:receipt-claim:review',
        'finance:receipt-claim:confirm',
        'finance:receipt-claim:reject',
        'finance:receipt-claim:revoke'
  )
  AND EXISTS (
        SELECT 1 FROM `system_menu`
        WHERE `deleted` = b'0' AND `component` = 'finance/receipt-claim/review'
  );
