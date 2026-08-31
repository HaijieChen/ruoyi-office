<script lang="ts" setup>
import SalaryDetail from './index.vue';

defineOptions({ name: 'FinanceSalaryPaymentFinanceDetail' });

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
  <SalaryDetail v-bind="$props" mode="finance" />
</template>
