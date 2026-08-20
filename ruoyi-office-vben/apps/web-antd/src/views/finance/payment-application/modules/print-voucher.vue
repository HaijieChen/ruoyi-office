<script lang="ts" setup>
import type { FinancePaymentApplicationApi } from '#/api/finance/payment-application';

import { Button } from 'ant-design-vue';
// @ts-ignore
import vPrint from 'vue3-print-nb';

defineOptions({ name: 'PaymentPrintVoucher' });
defineProps<{ detail: any }>();

const printObj = {
  id: 'paymentPrintVoucher',
  popTitle: '付款单',
  extraHead: '',
  zIndex: 20003,
};
</script>

<template>
  <div>
    <Button v-print="printObj" size="small">打印付款单</Button>
    <div id="paymentPrintVoucher" class="mt-3 bg-white p-6 text-sm text-black">
      <h2 class="mb-2 text-center text-xl font-bold">付款申请单</h2>
      <table class="w-full border-collapse">
        <tbody>
          <tr>
            <td class="w-1/4 border border-black p-1">单号</td>
            <td class="border border-black p-1">{{ detail.applicationNo }}</td>
            <td class="border border-black p-1">主体公司</td>
            <td class="border border-black p-1">{{ detail.entityCompanyName }}</td>
          </tr>
          <tr>
            <td class="border border-black p-1">收款方</td>
            <td class="border border-black p-1">{{ detail.payeeName }}</td>
            <td class="border border-black p-1">金额</td>
            <td class="border border-black p-1">{{ detail.applyAmount }} {{ detail.currency }}</td>
          </tr>
          <tr>
            <td class="border border-black p-1">账户</td>
            <td class="border border-black p-1" colspan="3">
              {{ detail.payeeBankName }} / {{ detail.payeeBankAccount }}
            </td>
          </tr>
          <tr>
            <td class="border border-black p-1">事由</td>
            <td class="border border-black p-1" colspan="3">{{ detail.paymentReason }}</td>
          </tr>
          <tr>
            <td class="border border-black p-1">支付日</td>
            <td class="border border-black p-1">{{ detail.actualPayDate || '-' }}</td>
            <td class="border border-black p-1">资料</td>
            <td class="border border-black p-1">
              {{ detail.materialsStatus === 'WAIT_INVOICE' ? '待补票' : '齐全' }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
