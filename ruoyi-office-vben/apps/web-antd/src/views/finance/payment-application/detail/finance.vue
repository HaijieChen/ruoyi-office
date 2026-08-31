<script lang="ts" setup>
import PaymentDetail from './index.vue';

defineOptions({ name: 'FinancePaymentApplicationFinanceDetail' });
</script>

<template>
  <PaymentDetail v-bind="$attrs" mode="finance" />
</template>
