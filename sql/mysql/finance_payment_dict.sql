-- 付款字典：费用项目 / 会计科目 / 支付方式 / 支付时效 / 付款事由（PAY-P1-4）
SET NAMES utf8mb4;

-- 字典类型
INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT t.name, t.type, 0, t.remark, 'admin', NOW(), 'admin', NOW(), b'0'
FROM (
    SELECT '费用归属项目' AS name, 'finance_cost_project' AS type, '付款申请费用项目' AS remark
    UNION ALL SELECT '费用会计科目', 'finance_accounting_subject', '付款财务节点科目'
    UNION ALL SELECT '付款支付方式', 'finance_pay_method', '电汇/支付宝/微信等'
    UNION ALL SELECT '支付时效', 'finance_payment_timing', '即时/月底/通知'
    UNION ALL SELECT '付款事由', 'finance_payment_reason', '业务/采购/薪资/税费/租赁/其他'
) t
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_type` d WHERE d.`deleted` = b'0' AND d.`type` = t.type
);

-- 字典数据（幂等按 type+value）
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT d.sort, d.label, d.value, d.dict_type, 0, '', '', '', 'admin', NOW(), 'admin', NOW(), b'0'
FROM (
    SELECT 1 AS sort, '百度充值' AS label, 'baidu_recharge' AS value, 'finance_cost_project' AS dict_type
    UNION ALL SELECT 2, '搜索推广', 'search_promo', 'finance_cost_project'
    UNION ALL SELECT 3, '办公采购', 'office_purchase', 'finance_cost_project'
    UNION ALL SELECT 4, '其他', 'other', 'finance_cost_project'
    UNION ALL SELECT 1, '管理费用', 'admin_expense', 'finance_accounting_subject'
    UNION ALL SELECT 2, '销售费用', 'sales_expense', 'finance_accounting_subject'
    UNION ALL SELECT 3, '主营业务成本', 'cogs', 'finance_accounting_subject'
    UNION ALL SELECT 1, '电汇', 'wire', 'finance_pay_method'
    UNION ALL SELECT 2, '支付宝', 'alipay', 'finance_pay_method'
    UNION ALL SELECT 3, '微信', 'wechat', 'finance_pay_method'
    UNION ALL SELECT 1, '即时', 'IMMEDIATE', 'finance_payment_timing'
    UNION ALL SELECT 2, '月底', 'MONTH_END', 'finance_payment_timing'
    UNION ALL SELECT 3, '通知', 'ON_NOTICE', 'finance_payment_timing'
    UNION ALL SELECT 1, '业务付款', 'BUSINESS', 'finance_payment_reason'
    UNION ALL SELECT 2, '采购付款', 'PURCHASE', 'finance_payment_reason'
    UNION ALL SELECT 3, '薪资', 'SALARY', 'finance_payment_reason'
    UNION ALL SELECT 4, '税费', 'TAX', 'finance_payment_reason'
    UNION ALL SELECT 5, '房屋租赁', 'LEASE', 'finance_payment_reason'
    UNION ALL SELECT 6, '其他', 'OTHER', 'finance_payment_reason'
) d
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` x
    WHERE x.`deleted` = b'0' AND x.`dict_type` = d.dict_type AND x.`value` = d.value
);
