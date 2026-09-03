<script lang="ts" setup>
import type { FinanceExpenseApi } from '#/api/finance/expense-reimbursement';

import { computed } from 'vue';

import { getDictLabel } from '@vben/hooks';

import { Button } from 'ant-design-vue';

import { printFormElement } from '#/utils/print-form';

defineOptions({ name: 'ExpensePrintVoucher' });

const props = defineProps<{ bill: FinanceExpenseApi.Bill }>();

function money(v: unknown) {
  const n = Number(v);
  if (!Number.isFinite(n)) return '-';
  return `¥${n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function categoryLabel(line: FinanceExpenseApi.Line) {
  return getDictLabel('finance_expense_category', line.category) || line.category || '未分类';
}

function subItemLabel(line: FinanceExpenseApi.Line) {
  if (line.subItem) {
    return getDictLabel('finance_expense_subitem', line.subItem) || line.subItem;
  }
  return props.bill.proxyTicket ? categoryLabel(line) : '—';
}

const printRows = computed(() => {
  const map = new Map<string, { category: string; subItem: string; amount: number }>();
  for (const line of props.bill.lines || []) {
    const category = categoryLabel(line);
    const subItem = subItemLabel(line);
    const key = props.bill.proxyTicket ? subItem : `${category}\0${subItem}`;
    const cur = map.get(key);
    const amount = Number(line.amount || 0);
    if (cur) {
      cur.amount += amount;
    } else {
      map.set(key, { category, subItem, amount });
    }
  }
  return [...map.values()];
});

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
            <th>单据编号</th>
            <td>{{ bill.applicationNo || '-' }}</td>
            <th>期间</th>
            <td>{{ bill.periodLabel }}</td>
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
          <tr>
            <th>申请金额</th>
            <td>{{ money(bill.applyAmount) }}</td>
            <th>实报金额</th>
            <td>{{ bill.approvedAmount == null ? '—' : money(bill.approvedAmount) }}</td>
          </tr>
        </tbody>
      </table>
      <table class="mt">
        <thead>
          <tr v-if="bill.proxyTicket">
            <th>费用子项目</th>
            <th>金额</th>
          </tr>
          <tr v-else>
            <th>费用项目</th>
            <th>费用子项目</th>
            <th>金额</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, i) in printRows" :key="i">
            <template v-if="bill.proxyTicket">
              <td>{{ row.subItem }}</td>
              <td>{{ money(row.amount) }}</td>
            </template>
            <template v-else>
              <td>{{ row.category }}</td>
              <td>{{ row.subItem }}</td>
              <td>{{ money(row.amount) }}</td>
            </template>
          </tr>
        </tbody>
      </table>
      <div class="foot">合计 {{ money(bill.applyAmount) }}</div>
    </div>
  </div>
</template>
