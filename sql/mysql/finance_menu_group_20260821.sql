
SET NAMES utf8mb4;

SET @fin := (SELECT id FROM system_menu WHERE deleted=b'0' AND parent_id=0 AND path IN ('/finance','finance') LIMIT 1);

INSERT INTO system_menu
  (name, permission, type, sort, parent_id, path, icon, component, component_name,
   status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT d.name, '', 1, d.sort, @fin, d.path, d.icon, NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM (
  SELECT '法务' name, 'legal' path, 10 sort, 'ep:stamp' icon
  UNION ALL SELECT '财务', 'fin-biz', 20, 'ep:money'
  UNION ALL SELECT '商务', 'commerce', 30, 'ep:briefcase'
  UNION ALL SELECT '基础数据维护', 'master-data', 40, 'ep:collection'
  UNION ALL SELECT '财务报表', 'fin-report', 50, 'ep:data-analysis'
) d
WHERE @fin IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM system_menu m WHERE m.deleted=b'0' AND m.parent_id=@fin AND m.path=d.path
  );

SET @legal := (SELECT id FROM system_menu WHERE deleted=b'0' AND parent_id=@fin AND path='legal' LIMIT 1);
SET @ops := (SELECT id FROM system_menu WHERE deleted=b'0' AND parent_id=@fin AND path='fin-biz' LIMIT 1);
SET @biz := (SELECT id FROM system_menu WHERE deleted=b'0' AND parent_id=@fin AND path='commerce' LIMIT 1);
SET @master := (SELECT id FROM system_menu WHERE deleted=b'0' AND parent_id=@fin AND path='master-data' LIMIT 1);
SET @rpt := (SELECT id FROM system_menu WHERE deleted=b'0' AND parent_id=@fin AND path='fin-report' LIMIT 1);

-- 法务：合同
UPDATE system_menu SET parent_id=@legal, path='/finance/contract-application', sort=1
WHERE id=5184 AND deleted=b'0';

-- 法务：用印（复用 OA 用印申请页面）
INSERT INTO system_menu
  (name, permission, type, sort, parent_id, path, icon, component, component_name,
   status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '用印申请', '', 2, 2, @legal, '/finance/seal-apply', 'ep:stamp', 'oa/seal/sealapply/list/index', 'OaSealApplyList',
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL
WHERE @legal IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.deleted=b'0' AND m.parent_id=@legal AND m.path='/finance/seal-apply');

-- 财务
UPDATE system_menu SET parent_id=@ops, path='/finance/receipt', sort=1 WHERE id=5052 AND deleted=b'0';
UPDATE system_menu SET parent_id=@ops, path='/finance/invoice-application', sort=2 WHERE id=5080 AND deleted=b'0';
UPDATE system_menu SET parent_id=@ops, path='/finance/payment-application', sort=3 WHERE id=5191 AND deleted=b'0';
UPDATE system_menu SET parent_id=@ops, path='/finance/salary-payment', sort=4 WHERE id=5219 AND deleted=b'0';
UPDATE system_menu SET parent_id=@ops, path='/finance/tax-payment', sort=5 WHERE id=5227 AND deleted=b'0';
UPDATE system_menu SET parent_id=@ops, path='/finance/expense-reimbursement', sort=6 WHERE id=5265 AND deleted=b'0';
UPDATE system_menu SET parent_id=@ops, path='/finance/expense-reimbursement/create', sort=7, visible=b'0' WHERE id=5266 AND deleted=b'0';
UPDATE system_menu SET parent_id=@ops, path='/finance/expense-reimbursement/detail', sort=8, visible=b'0' WHERE id=5267 AND deleted=b'0';
UPDATE system_menu SET parent_id=@ops, path='/finance/expense-reimbursement/no-invoice-create', sort=9, visible=b'0' WHERE id=5271 AND deleted=b'0';

-- 商务
UPDATE system_menu SET parent_id=@biz, path='/finance/business-order', sort=1 WHERE id=5055 AND deleted=b'0';
UPDATE system_menu SET parent_id=@biz, path='/finance/receipt-claim', sort=2 WHERE id=5063 AND deleted=b'0';
UPDATE system_menu SET parent_id=@biz, path='/finance/receipt-claim-review', sort=3 WHERE id=5073 AND deleted=b'0';

-- 基础数据
UPDATE system_menu SET parent_id=@master, path='/finance/customer-company', sort=1, name='客商档案' WHERE id=5175 AND deleted=b'0';
UPDATE system_menu SET parent_id=@master, path='/finance/company-bank-account', sort=2 WHERE id=5235 AND deleted=b'0';
UPDATE system_menu SET parent_id=@master, path='/finance/exchange-rate', sort=3 WHERE id=5273 AND deleted=b'0';

-- 报表
UPDATE system_menu SET parent_id=@rpt, path='/finance/report-ar-detail', sort=1 WHERE id=5255 AND deleted=b'0';
UPDATE system_menu SET parent_id=@rpt, path='/finance/report-gross-margin', sort=2 WHERE id=5259 AND deleted=b'0';
UPDATE system_menu SET parent_id=@rpt, path='/finance/report-bank-balance', sort=3 WHERE id=5260 AND deleted=b'0';
UPDATE system_menu SET parent_id=@rpt, path='/finance/report-dept-profit', sort=4 WHERE id=5261 AND deleted=b'0';

-- 挂在财务根上的按钮挪到对应页面/目录
UPDATE system_menu SET parent_id=5175 WHERE id=5179 AND deleted=b'0';
UPDATE system_menu SET parent_id=5235 WHERE id=5239 AND deleted=b'0';
UPDATE system_menu SET parent_id=5260 WHERE id=5251 AND deleted=b'0';
UPDATE system_menu SET parent_id=5260 WHERE id=5252 AND deleted=b'0';
UPDATE system_menu SET parent_id=5261 WHERE id=5254 AND deleted=b'0';


INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT DISTINCT rm.role_id, d.id, 'admin', NOW(), 'admin', NOW(), b'0', rm.tenant_id
FROM system_menu d
JOIN system_menu fin ON fin.deleted=b'0' AND fin.parent_id=0 AND fin.path IN ('/finance','finance')
JOIN system_role_menu rm ON rm.deleted=b'0' AND rm.menu_id=fin.id
WHERE d.deleted=b'0' AND d.parent_id=fin.id AND d.path IN ('legal','fin-biz','commerce','master-data','fin-report')
  AND NOT EXISTS (
    SELECT 1 FROM system_role_menu x WHERE x.deleted=b'0' AND x.role_id=rm.role_id AND x.menu_id=d.id AND x.tenant_id=rm.tenant_id
  );

INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT DISTINCT rm.role_id, s.id, 'admin', NOW(), 'admin', NOW(), b'0', rm.tenant_id
FROM system_menu s
JOIN system_role_menu rm ON rm.deleted=b'0' AND rm.menu_id=5184
WHERE s.deleted=b'0' AND s.path='/finance/seal-apply'
  AND NOT EXISTS (
    SELECT 1 FROM system_role_menu x WHERE x.deleted=b'0' AND x.role_id=rm.role_id AND x.menu_id=s.id AND x.tenant_id=rm.tenant_id
  );
