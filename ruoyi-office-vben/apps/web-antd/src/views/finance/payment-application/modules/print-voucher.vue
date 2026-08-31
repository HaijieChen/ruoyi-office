<script lang="ts" setup>
import { computed } from 'vue';

import PrintSlip from '#/components/print/print-slip.vue';
import { displayDate } from '#/utils/display-time';
import {
  financeProductLabel,
  financeReasonLabel,
  financeTimingLabel,
} from '#/views/finance/shared/display-labels';

defineOptions({ name: 'PaymentPrintVoucher' });

const props = defineProps<{ detail: any }>();

const fields = computed(() => {
  const rows = [
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
  ];
  if (props.detail?.paymentReason === 'BUSINESS') {
    rows.push({
      label: '产品类型',
      value: financeProductLabel(props.detail?.costProject),
    });
  }
  rows.push(
    { label: '支付日', value: displayDate(props.detail?.actualPayDate) },
    {
      label: '资料',
      value: props.detail?.materialsStatus === 'WAIT_INVOICE' ? '待补票' : '齐全',
    },
  );
  return rows;
});
</script>

<template>
  <PrintSlip
    slip-id="paymentPrintVoucher"
    title="付款申请单"
    :fields="fields"
  />
</template>
