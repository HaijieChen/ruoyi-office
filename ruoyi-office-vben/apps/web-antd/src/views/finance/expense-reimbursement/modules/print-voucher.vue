<script lang="ts" setup>
import type { FinanceExpenseApi } from '#/api/finance/expense-reimbursement';

import { getDictLabel } from '@vben/hooks';

import { Button } from 'ant-design-vue';
import dayjs from 'dayjs';

import { printFormElement } from '#/utils/print-form';

defineOptions({ name: 'ExpensePrintVoucher' });
defineProps<{ bill: FinanceExpenseApi.Bill }>();

function formatFeeDate(v: unknown) {
  if (v == null || v === '') return '';
  if (Array.isArray(v) && v.length >= 3) {
    return `${v[0]}-${String(v[1]).padStart(2, '0')}-${String(v[2]).padStart(2, '0')}`;
  }
  if (typeof v === 'number') {
    return dayjs(v < 1e12 ? v * 1000 : v).format('YYYY-MM-DD');
  }
  const s = String(v);
  if (/^\d{10,13}$/.test(s)) {
    const n = Number(s);
    return dayjs(n < 1e12 ? n * 1000 : n).format('YYYY-MM-DD');
  }
  return s.length >= 10 ? s.slice(0, 10) : s;
}

function categoryText(line: FinanceExpenseApi.Line) {
  const cat = getDictLabel('finance_expense_category', line.category) || line.category || '';
  const sub = line.subItem
    ? getDictLabel('finance_expense_subitem', line.subItem) || line.subItem
    : '';
  return [cat, sub].filter(Boolean).join('/');
}

function onPrint() {
  printFormElement(document.getElementById('expensePrintVoucher'), '费用报销单');
}
</script>

<template>
  <div>
    <Button type="primary" class="print:hidden" @click="onPrint">打印</Button>
    <div id="expensePrintVoucher" class="finance-print-slip hidden">
      <h1>费用报销单</h1>
      <table>
        <tbody>
          <tr>
            <th>标题</th>
            <td>{{ bill.processTitle }}</td>
            <th>单据编号</th>
            <td>{{ bill.applicationNo || '-' }}</td>
          </tr>
          <tr>
            <th>期间</th>
            <td colspan="3">{{ bill.periodLabel }}</td>
          </tr>
          <tr>
            <th>主体公司</th>
            <td colspan="3">{{ bill.entityCompanyName || '-' }}</td>
          </tr>
          <tr>
            <th>收款户名</th>
            <td>{{ bill.payeeAccountName }}</td>
            <th>开户行</th>
            <td>{{ bill.payeeBankName || '—' }}</td>
          </tr>
          <tr>
            <th>账号</th>
            <td colspan="3">{{ bill.payeeAccountNo }}</td>
          </tr>
        </tbody>
      </table>
      <table class="mt">
        <thead>
          <tr>
            <th>日期</th>
            <th>费用类型</th>
            <th>发票类型</th>
            <th>金额</th>
            <th>专票税额</th>
            <th>票号</th>
            <th>说明</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(line, i) in bill.lines || []" :key="i">
            <td>{{ formatFeeDate(line.feeDate) }}</td>
            <td>{{ categoryText(line) }}</td>
            <td>{{ line.invoiceType || '-' }}</td>
            <td>{{ line.amount }}</td>
            <td>{{ line.taxAmount ?? '' }}</td>
            <td>{{ line.invoiceNo || '' }}</td>
            <td>{{ line.remark || '' }}</td>
          </tr>
        </tbody>
      </table>
      <div class="foot">
        合计 {{ bill.applyAmount }}　专票税额
        {{
          (bill.lines || []).reduce((s, l) => s + Number(l.taxAmount || 0), 0)
        }}　实报 {{ bill.approvedAmount ?? '-' }}
      </div>
    </div>
  </div>
</template>
