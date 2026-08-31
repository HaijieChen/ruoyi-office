<script lang="ts" setup>
import SalaryDetail from './index.vue';

defineOptions({ name: 'FinanceSalaryPaymentFinanceDetail' });
</script>

<template>
  <SalaryDetail v-bind="$attrs" mode="finance" />
</template>
