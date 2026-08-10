-- 付款字典：费用项目 / 费用科目性质 / 支付方式 / 支付时效 / 付款事由（PAY-P1-4）
SET NAMES utf8mb4;

-- 字典类型
INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT t.name, t.type, 0, t.remark, 'admin', NOW(), 'admin', NOW(), b'0'
FROM (
    SELECT '费用归属项目' AS name, 'finance_cost_project' AS type, '付款申请费用项目' AS remark
    UNION ALL SELECT '费用科目/性质', 'finance_accounting_subject', '仅付款财务审批节点填写的费用科目/性质'
    UNION ALL SELECT '付款支付方式', 'finance_pay_method', '电汇/支付宝/微信等'
    UNION ALL SELECT '支付时效', 'finance_payment_timing', '即时/月底/通知'
    UNION ALL SELECT '付款事由', 'finance_payment_reason', '业务/采购/薪资/税费/租赁/其他'
) t
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_type` d WHERE d.`deleted` = b'0' AND d.`type` = t.type
);

UPDATE `system_dict_type`
SET `name` = '费用科目/性质', `remark` = '仅付款财务审批节点填写的费用科目/性质',
    `updater` = 'admin', `update_time` = NOW()
WHERE `type` = 'finance_accounting_subject' AND `deleted` = b'0';

-- 费用科目/性质以最新下拉框选项为准；旧的通用会计科目保留历史数据但不再作为可选项。
UPDATE `system_dict_data`
SET `deleted` = b'1', `updater` = 'admin', `update_time` = NOW()
WHERE `dict_type` = 'finance_accounting_subject'
  AND `deleted` = b'0'
  AND `value` NOT IN (
    '营业成本-软件', '营业成本-搜索', '营业成本-风灵月影', '营业成本-独代',
    '营业成本-服务商', '营业成本-小红书', '营业成本-游戏联运', '营业成本-媒体代理',
    '营业成本-营销业务', '营业成本-硬件', '营业成本-CDK', '营业成本-游戏发行',
    '营业成本-机房服务器', '营业成本-卡饭论坛', '营业成本-IP费', '营业成本-其他',
    '营业成本-清泷加速器', '营业费用-办公费', '营业费用-财务软件', '营业费用-差旅费',
    '营业费用-代账公司', '营业费用-电子设备', '营业费用-房租水电费', '营业费用-福利费',
    '营业费用-快递费', '营业费用-律师费', '营业费用-软著费', '营业费用-审计费',
    '营业费用-网络宽带费', '营业费用-域名', '营业费用-账号认证费', '营业费用-招待费',
    '营业外收入', '营业外支出', '用人成本-辞退金', '用人成本-工资社保',
    '用人成本-劳务外包', '财务费用-手续费', '税金支出', '其他应收款'
  );

UPDATE `system_dict_data`
SET `deleted` = b'0', `status` = 0, `updater` = 'admin', `update_time` = NOW()
WHERE `dict_type` = 'finance_accounting_subject'
  AND `value` IN (
    '营业成本-软件', '营业成本-搜索', '营业成本-风灵月影', '营业成本-独代',
    '营业成本-服务商', '营业成本-小红书', '营业成本-游戏联运', '营业成本-媒体代理',
    '营业成本-营销业务', '营业成本-硬件', '营业成本-CDK', '营业成本-游戏发行',
    '营业成本-机房服务器', '营业成本-卡饭论坛', '营业成本-IP费', '营业成本-其他',
    '营业成本-清泷加速器', '营业费用-办公费', '营业费用-财务软件', '营业费用-差旅费',
    '营业费用-代账公司', '营业费用-电子设备', '营业费用-房租水电费', '营业费用-福利费',
    '营业费用-快递费', '营业费用-律师费', '营业费用-软著费', '营业费用-审计费',
    '营业费用-网络宽带费', '营业费用-域名', '营业费用-账号认证费', '营业费用-招待费',
    '营业外收入', '营业外支出', '用人成本-辞退金', '用人成本-工资社保',
    '用人成本-劳务外包', '财务费用-手续费', '税金支出', '其他应收款'
  );

-- 字典数据（幂等按 type+value）
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT d.sort, d.label, d.value, d.dict_type, 0, '', '', '', 'admin', NOW(), 'admin', NOW(), b'0'
FROM (
    SELECT 1 AS sort, '百度充值' AS label, 'baidu_recharge' AS value, 'finance_cost_project' AS dict_type
    UNION ALL SELECT 2, '搜索推广', 'search_promo', 'finance_cost_project'
    UNION ALL SELECT 3, '办公采购', 'office_purchase', 'finance_cost_project'
    UNION ALL SELECT 4, '其他', 'other', 'finance_cost_project'
    UNION ALL SELECT 1, '营业成本-软件', '营业成本-软件', 'finance_accounting_subject'
    UNION ALL SELECT 2, '营业成本-搜索', '营业成本-搜索', 'finance_accounting_subject'
    UNION ALL SELECT 3, '营业成本-风灵月影', '营业成本-风灵月影', 'finance_accounting_subject'
    UNION ALL SELECT 4, '营业成本-独代', '营业成本-独代', 'finance_accounting_subject'
    UNION ALL SELECT 5, '营业成本-服务商', '营业成本-服务商', 'finance_accounting_subject'
    UNION ALL SELECT 6, '营业成本-小红书', '营业成本-小红书', 'finance_accounting_subject'
    UNION ALL SELECT 7, '营业成本-游戏联运', '营业成本-游戏联运', 'finance_accounting_subject'
    UNION ALL SELECT 8, '营业成本-媒体代理', '营业成本-媒体代理', 'finance_accounting_subject'
    UNION ALL SELECT 9, '营业成本-营销业务', '营业成本-营销业务', 'finance_accounting_subject'
    UNION ALL SELECT 10, '营业成本-硬件', '营业成本-硬件', 'finance_accounting_subject'
    UNION ALL SELECT 11, '营业成本-CDK', '营业成本-CDK', 'finance_accounting_subject'
    UNION ALL SELECT 12, '营业成本-游戏发行', '营业成本-游戏发行', 'finance_accounting_subject'
    UNION ALL SELECT 13, '营业成本-机房服务器', '营业成本-机房服务器', 'finance_accounting_subject'
    UNION ALL SELECT 14, '营业成本-卡饭论坛', '营业成本-卡饭论坛', 'finance_accounting_subject'
    UNION ALL SELECT 15, '营业成本-IP费', '营业成本-IP费', 'finance_accounting_subject'
    UNION ALL SELECT 16, '营业成本-其他', '营业成本-其他', 'finance_accounting_subject'
    UNION ALL SELECT 17, '营业成本-清泷加速器', '营业成本-清泷加速器', 'finance_accounting_subject'
    UNION ALL SELECT 18, '营业费用-办公费', '营业费用-办公费', 'finance_accounting_subject'
    UNION ALL SELECT 19, '营业费用-财务软件', '营业费用-财务软件', 'finance_accounting_subject'
    UNION ALL SELECT 20, '营业费用-差旅费', '营业费用-差旅费', 'finance_accounting_subject'
    UNION ALL SELECT 21, '营业费用-代账公司', '营业费用-代账公司', 'finance_accounting_subject'
    UNION ALL SELECT 22, '营业费用-电子设备', '营业费用-电子设备', 'finance_accounting_subject'
    UNION ALL SELECT 23, '营业费用-房租水电费', '营业费用-房租水电费', 'finance_accounting_subject'
    UNION ALL SELECT 24, '营业费用-福利费', '营业费用-福利费', 'finance_accounting_subject'
    UNION ALL SELECT 25, '营业费用-快递费', '营业费用-快递费', 'finance_accounting_subject'
    UNION ALL SELECT 26, '营业费用-律师费', '营业费用-律师费', 'finance_accounting_subject'
    UNION ALL SELECT 27, '营业费用-软著费', '营业费用-软著费', 'finance_accounting_subject'
    UNION ALL SELECT 28, '营业费用-审计费', '营业费用-审计费', 'finance_accounting_subject'
    UNION ALL SELECT 29, '营业费用-网络宽带费', '营业费用-网络宽带费', 'finance_accounting_subject'
    UNION ALL SELECT 30, '营业费用-域名', '营业费用-域名', 'finance_accounting_subject'
    UNION ALL SELECT 31, '营业费用-账号认证费', '营业费用-账号认证费', 'finance_accounting_subject'
    UNION ALL SELECT 32, '营业费用-招待费', '营业费用-招待费', 'finance_accounting_subject'
    UNION ALL SELECT 33, '营业外收入', '营业外收入', 'finance_accounting_subject'
    UNION ALL SELECT 34, '营业外支出', '营业外支出', 'finance_accounting_subject'
    UNION ALL SELECT 35, '用人成本-辞退金', '用人成本-辞退金', 'finance_accounting_subject'
    UNION ALL SELECT 36, '用人成本-工资社保', '用人成本-工资社保', 'finance_accounting_subject'
    UNION ALL SELECT 37, '用人成本-劳务外包', '用人成本-劳务外包', 'finance_accounting_subject'
    UNION ALL SELECT 38, '财务费用-手续费', '财务费用-手续费', 'finance_accounting_subject'
    UNION ALL SELECT 39, '税金支出', '税金支出', 'finance_accounting_subject'
    UNION ALL SELECT 40, '其他应收款', '其他应收款', 'finance_accounting_subject'
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
