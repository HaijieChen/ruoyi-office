<script lang="ts" setup>
import TaxDetail from './index.vue';

defineOptions({ name: 'FinanceTaxPaymentFinanceDetail' });

defineProps<{
  activityNodes?: any[];
  id?: number | string;
  isApproval?: boolean;
  nodeKey?: string;
  nodeKeyName?: string;
  processDefinition?: any;
  processInstance?: any;
  taskId?: string;
}>();
</script>

<template>
  <TaxDetail v-bind="$props" mode="finance" />
</template>
