<script lang="ts" setup>
import type { FinanceExpenseApi } from '#/api/finance/expense-reimbursement';

import { getDictLabel } from '@vben/hooks';

import { Button } from 'ant-design-vue';
import dayjs from 'dayjs';

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

function categoryText(bill: FinanceExpenseApi.Bill, line: FinanceExpenseApi.Line) {
  if (bill.proxyTicket) {
    return line.invoiceType || '-';
  }
  const cat = getDictLabel('finance_expense_category', line.category) || line.category || '';
  const sub = line.subItem
    ? getDictLabel('finance_expense_subitem', line.subItem) || line.subItem
    : '';
  return [cat, sub].filter(Boolean).join('/');
}

function onPrint() {
  window.print();
}
</script>

<template>
  <div>
    <Button class="print:hidden" @click="onPrint">打印报销单</Button>
    <div id="expensePrintVoucher" class="mt-3 bg-white p-6 text-sm text-black">
      <h2 class="mb-2 text-center text-xl font-bold">费用报销单</h2>
      <div class="mb-2 flex justify-between">
        <span>标题：{{ bill.processTitle }}</span>
        <span>期间：{{ bill.periodLabel }}</span>
      </div>
      <div class="mb-2 flex justify-between">
        <span>主体公司：{{ bill.entityCompanyName || '-' }}</span>
      </div>
      <div class="mb-2 flex justify-between">
        <span>收款户名：{{ bill.payeeAccountName }}</span>
        <span>账号：{{ bill.payeeAccountNo }}</span>
      </div>
      <table class="w-full border-collapse">
        <thead>
          <tr>
            <th class="border border-black p-1">日期</th>
            <th class="border border-black p-1">{{ bill.proxyTicket ? '发票类型' : '费用类型' }}</th>
            <th class="border border-black p-1">金额</th>
            <th class="border border-black p-1">票号</th>
            <th class="border border-black p-1">说明</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(line, i) in bill.lines || []" :key="i">
            <td class="border border-black p-1">{{ formatFeeDate(line.feeDate) }}</td>
            <td class="border border-black p-1">{{ categoryText(bill, line) }}</td>
            <td class="border border-black p-1 text-right">{{ line.amount }}</td>
            <td class="border border-black p-1">{{ line.invoiceNo || '' }}</td>
            <td class="border border-black p-1">{{ line.remark || '' }}</td>
          </tr>
        </tbody>
      </table>
      <div class="mt-2">合计 {{ bill.applyAmount }}　实报 {{ bill.approvedAmount ?? '-' }}</div>
    </div>
  </div>
</template>
