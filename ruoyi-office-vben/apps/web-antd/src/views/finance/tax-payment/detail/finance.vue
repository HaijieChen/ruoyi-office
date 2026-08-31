<script lang="ts" setup>
import TaxDetail from './index.vue';

defineOptions({ name: 'FinanceTaxPaymentFinanceDetail' });
</script>

<template>
  <TaxDetail v-bind="$attrs" mode="finance" />
</template>
