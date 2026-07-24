-- 菜单：银行回单认领
-- 父菜单假设已存在：finance（目录），id 由实际环境决定
-- 脚本使用 @parent_id 变量，执行前请替换为实际父菜单 ID

SET @parent_id = (
  SELECT menu_id FROM sys_menu WHERE menu_name = '财务管理' AND menu_type = 'M' LIMIT 1
);

-- 认领管理（目录）
INSERT IGNORE INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, remark)
VALUES ('回单认领', @parent_id, 30, 'receipt-claim', NULL, 1, 0, 'M', '0', '0', '', 'money', 'admin', '银行回单认领管理');

SET @claim_dir_id = LAST_INSERT_ID();

-- 我的认领单（菜单）
INSERT IGNORE INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, remark)
VALUES ('我的认领单', @claim_dir_id, 1, 'my', 'finance/receipt-claim/index', 1, 0, 'C', '0', '0', 'finance:receipt-claim:my', 'list', 'admin', '我的银行回单认领单');

-- 财务审核（菜单）
INSERT IGNORE INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, remark)
VALUES ('认领审核', @claim_dir_id, 2, 'review', 'finance/receipt-claim/review', 1, 0, 'C', '0', '0', 'finance:receipt-claim:review', 'check', 'admin', '财务认领单审核');

-- 审核操作按钮权限
INSERT IGNORE INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, remark)
VALUES ('认领审核操作', (SELECT menu_id FROM sys_menu WHERE perms = 'finance:receipt-claim:review' LIMIT 1), 1, '', '', 1, 0, 'F', '0', '0', 'finance:receipt-claim:review', '#', 'admin', '认领审核按钮');
