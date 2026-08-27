SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `finance_handling_fee_payment` (
    `id`                       bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `fee_date`                 date NOT NULL COMMENT '付款日期',
    `amount`                   decimal(18,2) NOT NULL COMMENT '金额',
    `currency`                 varchar(16) NOT NULL COMMENT '币种 CNY/USD/HKD',
    `entity_company_dept_id`   bigint NOT NULL COMMENT '主体公司=system_dept.id',
    `entity_company_name`      varchar(255) NOT NULL COMMENT '主体公司名称快照',
    `company_bank_account_id`  bigint NOT NULL COMMENT '公司银行账户编号',
    `account_name`             varchar(128) NOT NULL COMMENT '户名快照',
    `bank_name`                varchar(255) NOT NULL COMMENT '开户行快照',
    `account_no`               varchar(128) NOT NULL COMMENT '银行账号快照',
    `account_no_masked`        varchar(64) NOT NULL COMMENT '账号掩码',
    `creator`                  varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time`              datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`                  varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time`              datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                  bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`                bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_fhfp_fee_date` (`fee_date`),
    KEY `idx_fhfp_entity_company` (`entity_company_dept_id`),
    KEY `idx_fhfp_tenant` (`tenant_id`)
) ENGINE=InnoDB COMMENT='财务手续费付款台账';

-- 手续费付款菜单 / 按钮权限 / finance_admin 授权（幂等）
-- 挂 fin-biz，与银行到款同级：到款 sort=1，本页 sort=2；开票及之后可见页 sort 用 component 精确定位，禁止写死 menu id。

SET @fin := (SELECT id FROM system_menu WHERE deleted=b'0' AND parent_id=0 AND path IN ('/finance','finance') LIMIT 1);
SET @ops := (SELECT id FROM system_menu WHERE deleted=b'0' AND parent_id=@fin AND path='fin-biz' LIMIT 1);

-- 开票及之后 fin-biz 页面 sort>=2 后移（只动 type=2 可见页，避免重复执行多次漂移：用 component 精确定位）
UPDATE system_menu SET sort = 3 WHERE deleted=b'0' AND parent_id=@ops AND component='finance/invoice-application/index'; -- 开票
UPDATE system_menu SET sort = 4 WHERE deleted=b'0' AND parent_id=@ops AND component='finance/payment-application/index';
UPDATE system_menu SET sort = 5 WHERE deleted=b'0' AND parent_id=@ops AND component='finance/salary-payment/index';
UPDATE system_menu SET sort = 6 WHERE deleted=b'0' AND parent_id=@ops AND component='finance/tax-payment/index';
UPDATE system_menu SET sort = 7 WHERE deleted=b'0' AND parent_id=@ops AND component='finance/expense-reimbursement/index';

INSERT INTO system_menu
  (name, permission, type, sort, parent_id, path, icon, component, component_name,
   status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '手续费付款', '', 2, 2, @ops, '/finance/handling-fee-payment', 'ep:coin',
       'finance/handling-fee-payment/index', 'FinanceHandlingFeePayment',
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @ops IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM system_menu m WHERE m.deleted=b'0' AND m.component='finance/handling-fee-payment/index'
  );

INSERT INTO system_menu
  (name, permission, type, sort, parent_id, path, icon, component, component_name,
   status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT btn.name, btn.permission, 3, btn.sort, parent.id, '', '', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM (
    SELECT '手续费付款查询' name, 'finance:handling-fee-payment:query' permission, 1 sort
    UNION ALL SELECT '手续费付款创建', 'finance:handling-fee-payment:create', 2
    UNION ALL SELECT '手续费付款更新', 'finance:handling-fee-payment:update', 3
    UNION ALL SELECT '手续费付款删除', 'finance:handling-fee-payment:delete', 4
    UNION ALL SELECT '手续费付款导入', 'finance:handling-fee-payment:import', 5
) btn
CROSS JOIN (
    SELECT id FROM system_menu WHERE deleted=b'0' AND component='finance/handling-fee-payment/index' LIMIT 1
) parent
WHERE NOT EXISTS (
    SELECT 1 FROM system_menu m WHERE m.deleted=b'0' AND m.permission=btn.permission
);

INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT r.id, m.id, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM system_role r
CROSS JOIN system_menu m
WHERE r.deleted=b'0' AND r.code='finance_admin' AND r.tenant_id=1
  AND m.deleted=b'0'
  AND (
        m.component='finance/handling-fee-payment/index'
     OR m.permission IN (
            'finance:handling-fee-payment:query',
            'finance:handling-fee-payment:create',
            'finance:handling-fee-payment:update',
            'finance:handling-fee-payment:delete',
            'finance:handling-fee-payment:import'
        )
  )
  AND NOT EXISTS (
      SELECT 1 FROM system_role_menu rm
      WHERE rm.role_id=r.id AND rm.menu_id=m.id AND rm.tenant_id=1 AND rm.deleted=b'0'
  );
