<script lang="ts" setup>
import { computed } from 'vue';

import PrintSlip from '#/components/print/print-slip.vue';
import { displayDate } from '#/utils/display-time';
import { financeReasonLabel, financeTimingLabel } from '#/views/finance/shared/display-labels';

defineOptions({ name: 'SalaryPrintVoucher' });

const props = defineProps<{ detail: any }>();

const fields = computed(() => [
  { label: '单号', value: props.detail?.applicationNo },
  { label: '主体公司', value: props.detail?.entityCompanyName },
  { label: '收款方', value: props.detail?.payeeName },
  {
    label: '金额',
    value: `${props.detail?.applyAmount ?? '-'} ${props.detail?.currency || ''}`.trim(),
  },
  {
    label: '账户',
    value: `${props.detail?.payeeBankName || '-'} / ${props.detail?.payeeBankAccount || '-'}`,
    span: 2 as const,
  },
  { label: '事由', value: financeReasonLabel(props.detail?.paymentReason), span: 2 as const },
  { label: '时效', value: financeTimingLabel(props.detail?.paymentTiming) },
  { label: '期间', value: props.detail?.periodLabel },
  { label: '支付日', value: displayDate(props.detail?.actualPayDate) },
  { label: '已登记支付', value: props.detail?.paidLineSum ?? 0 },
]);
</script>

<template>
  <PrintSlip
    slip-id="salaryPrintVoucher"
    title="薪资付款申请单"
    :fields="fields"
  />
</template>
