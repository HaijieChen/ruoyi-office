<script lang="ts" setup>
import TaxDetail from './index.vue';

defineOptions({ name: 'FinanceTaxPaymentCashierDetail' });
</script>

<template>
  <TaxDetail v-bind="$attrs" mode="cashier" />
</template>
