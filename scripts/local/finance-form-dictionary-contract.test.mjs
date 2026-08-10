import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');

const read = (relativePath) =>
  fs.readFileSync(path.join(repoRoot, relativePath), 'utf8');

const accountingSubjects = [
  '营业成本-软件',
  '营业成本-搜索',
  '营业成本-风灵月影',
  '营业成本-独代',
  '营业成本-服务商',
  '营业成本-小红书',
  '营业成本-游戏联运',
  '营业成本-媒体代理',
  '营业成本-营销业务',
  '营业成本-硬件',
  '营业成本-CDK',
  '营业成本-游戏发行',
  '营业成本-机房服务器',
  '营业成本-卡饭论坛',
  '营业成本-IP费',
  '营业成本-其他',
  '营业成本-清泷加速器',
  '营业费用-办公费',
  '营业费用-财务软件',
  '营业费用-差旅费',
  '营业费用-代账公司',
  '营业费用-电子设备',
  '营业费用-房租水电费',
  '营业费用-福利费',
  '营业费用-快递费',
  '营业费用-律师费',
  '营业费用-软著费',
  '营业费用-审计费',
  '营业费用-网络宽带费',
  '营业费用-域名',
  '营业费用-账号认证费',
  '营业费用-招待费',
  '营业外收入',
  '营业外支出',
  '用人成本-辞退金',
  '用人成本-工资社保',
  '用人成本-劳务外包',
  '财务费用-手续费',
  '税金支出',
  '其他应收款',
];

const productTypes = [
  '软件',
  '搜索',
  '风灵月影',
  '独代',
  '服务商',
  '小红书',
  '游戏联运',
  '媒体代理',
  '营销业务',
  '硬件',
  'CDK',
  '游戏发行',
];

const receiptFundTypes = ['其他收益-政府补助', '利息收入', '其他应付款'];

test('latest finance dictionary options are represented in seed SQL', () => {
  const paymentSql = read('sql/mysql/finance_payment_dict.sql');
  const receiptSql = read('sql/mysql/finance_fund_type_remark_dict.sql');
  const productSql = read('sql/mysql/finance_product_type_dict.sql');

  assert.match(paymentSql, /finance_accounting_subject/);
  for (const value of accountingSubjects) {
    assert.match(paymentSql, new RegExp(`'${value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}'`));
  }

  for (const value of receiptFundTypes) {
    assert.match(
      receiptSql,
      new RegExp(`'${value.replace(/[.*+?^${}()|[\\]\\\\]/g, '\\\\$&')}'`),
    );
  }
  assert.match(
    receiptSql,
    /SELECT\s+1,\s*'其他收益-政府补助',\s*'其他收益-政府补助',\s*'finance_fund_type_remark'/,
  );
  assert.match(receiptSql, /往来款项/);
  assert.match(receiptSql, /`deleted`\s*=\s*b'1'/);

  assert.match(productSql, /finance_product_type/);
  for (const value of productTypes) {
    assert.match(productSql, new RegExp(`'${value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}'`));
  }
});

test('finance forms expose the requested field contracts', () => {
  const paymentForm = read(
    'ruoyi-office-vben/apps/web-antd/src/views/finance/payment-application/modules/form-body.vue',
  );
  const paymentDetail = read(
    'ruoyi-office-vben/apps/web-antd/src/views/finance/payment-application/detail/index.vue',
  );
  const receiptForm = read('ruoyi-office-vben/apps/web-antd/src/views/finance/receipt/modules/form.vue');
  const contractForm = read(
    'ruoyi-office-vben/apps/web-antd/src/views/finance/contract-application/modules/form-body.vue',
  );
  const invoiceForm = read(
    'ruoyi-office-vben/apps/web-antd/src/views/finance/invoice-application/modules/form-body.vue',
  );
  const invoiceApi = read('ruoyi-office-vben/apps/web-antd/src/api/finance/invoice-application/index.ts');

  assert.doesNotMatch(paymentForm, /accountingSubject/);
  assert.match(paymentDetail, /isFinanceNode/);
  assert.match(paymentDetail, /finance_accounting_subject/);
  assert.match(paymentDetail, /费用科目\/性质/);
  assert.match(receiptForm, /finance_fund_type_remark/);

  assert.match(contractForm, /FINANCE_PRODUCT_TYPE/);
  assert.match(contractForm, /getDictOptions/);
  assert.match(contractForm, /formData\.productType/);
  assert.doesNotMatch(contractForm, /<Input[^>]+formData\.productType/);

  assert.match(invoiceForm, /taxContent/);
  assert.match(invoiceForm, /FINANCE_PRODUCT_TYPE/);
  assert.match(invoiceForm, /getDictOptions/);
  assert.match(invoiceForm, /产品类型/);
  assert.match(invoiceApi, /taxContent\?: string/);
});
