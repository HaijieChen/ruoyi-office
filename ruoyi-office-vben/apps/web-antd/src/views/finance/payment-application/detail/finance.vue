<script lang="ts" setup>
import PaymentDetail from './index.vue';

defineOptions({ name: 'FinancePaymentApplicationFinanceDetail' });

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
  <PaymentDetail v-bind="$props" mode="finance" />
</template>
