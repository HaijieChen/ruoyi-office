<script lang="ts" setup>
import type { FinanceExpenseApi } from '#/api/finance/expense-reimbursement';

import { Button } from 'ant-design-vue';
// @ts-ignore
import vPrint from 'vue3-print-nb';

defineOptions({ name: 'ExpensePrintVoucher' });
defineProps<{ bill: FinanceExpenseApi.Bill }>();

const printObj = {
  id: 'expensePrintVoucher',
  popTitle: '费用报销单',
  extraHead: '',
  zIndex: 20003,
};
</script>

<template>
  <div>
    <Button v-print="printObj" class="print:hidden">打印报销单</Button>
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
            <td class="border border-black p-1">{{ line.feeDate }}</td>
            <td class="border border-black p-1">
              {{
                bill.proxyTicket
                  ? line.invoiceType || '-'
                  : [line.category, line.subItem].filter(Boolean).join('/')
              }}
            </td>
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
