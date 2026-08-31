<script lang="ts" setup>
import PaymentDetail from './index.vue';

defineOptions({ name: 'FinancePaymentApplicationCashierDetail' });
</script>

<template>
  <PaymentDetail v-bind="$attrs" mode="cashier" />
</template>
