<script lang="ts" setup>
import SalaryDetail from './index.vue';

defineOptions({ name: 'FinanceSalaryPaymentCashierDetail' });
</script>

<template>
  <SalaryDetail v-bind="$attrs" mode="cashier" />
</template>
