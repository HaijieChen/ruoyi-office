<script lang="ts" setup>
import { computed } from 'vue';

import PrintSlip from '#/components/print/print-slip.vue';
import { displayDateTime } from '#/utils/display-time';

defineOptions({ name: 'InvoicePrintVoucher' });

const props = defineProps<{ detail: any }>();

const fields = computed(() => [
  { label: '申请单号', value: props.detail?.applicationNo },
  {
    label: '价税合计',
    value:
      props.detail?.totalAmount != null
        ? `¥${Number(props.detail.totalAmount).toFixed(2)}`
        : '-',
  },
  { label: '购方名称', value: props.detail?.buyerName },
  { label: '购方税号', value: props.detail?.buyerTaxNo },
  { label: '地址电话', value: props.detail?.buyerAddressPhone, span: 2 as const },
  { label: '开户行账号', value: props.detail?.buyerBankAccount, span: 2 as const },
  { label: '主体公司', value: props.detail?.invoiceCompany },
  { label: '发票类型', value: props.detail?.invoiceType },
  { label: '产品类型', value: props.detail?.taxContent },
  { label: '创建时间', value: displayDateTime(props.detail?.createTime) },
  {
    label: '特别开票要求',
    value: props.detail?.specialInvoiceRequirement,
    span: 2 as const,
  },
  { label: '备注', value: props.detail?.remark, span: 2 as const },
]);

const lineRows = computed(() =>
  (props.detail?.lines || []).map((line: any) => [
    line.businessOrderNo || (line.businessOrderId != null ? `#${line.businessOrderId}` : '-'),
    line.productType || line.currentProductType || '-',
    line.amount != null ? `¥${Number(line.amount).toFixed(2)}` : '-',
    line.billingPeriod || '-',
    line.invoiceNo || '-',
  ]),
);
</script>

<template>
  <PrintSlip
    slip-id="invoicePrintVoucher"
    title="开票申请单"
    :fields="fields"
    :line-columns="['商务单号', '产品', '开票金额', '账期', '票号']"
    :line-rows="lineRows"
    :footer="
      detail?.totalAmount != null
        ? `合计 ¥${Number(detail.totalAmount).toFixed(2)}`
        : ''
    "
  />
</template>
