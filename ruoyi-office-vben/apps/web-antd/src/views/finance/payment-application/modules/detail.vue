<script lang="ts" setup>
import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Descriptions, Spin } from 'ant-design-vue';

import { getPaymentApplication } from '#/api/finance/payment-application';
import type { FinancePaymentApplicationApi } from '#/api/finance/payment-application';

defineOptions({ name: 'FinancePaymentApplicationDetail' });

const detail = ref<FinancePaymentApplicationApi.Application | null>(null);
const loading = ref(false);

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      detail.value = null;
      return;
    }
    const data = modalApi.getData<{ id?: number }>() || {};
    if (!data.id) return;
    loading.value = true;
    try {
      detail.value = await getPaymentApplication(data.id);
    } finally {
      loading.value = false;
    }
  },
});
</script>

<template>
  <Modal title="付款申请详情" class="w-[720px]" :show-confirm-button="false">
    <Spin :spinning="loading">
      <Descriptions v-if="detail" bordered :column="1" size="small">
        <Descriptions.Item label="单号">{{ detail.applicationNo }}</Descriptions.Item>
        <Descriptions.Item label="标题">{{ detail.processTitle }}</Descriptions.Item>
        <Descriptions.Item label="状态">{{ detail.status }}</Descriptions.Item>
        <Descriptions.Item label="节点">{{ detail.currentNodeName }}</Descriptions.Item>
        <Descriptions.Item label="收款方">{{ detail.payeeName }}</Descriptions.Item>
        <Descriptions.Item label="账户">
          {{ detail.payeeBankName }} / {{ detail.payeeBankAccount }}
        </Descriptions.Item>
        <Descriptions.Item label="金额">{{ detail.applyAmount }} {{ detail.currency }}</Descriptions.Item>
        <Descriptions.Item label="时效">{{ detail.paymentTiming }}</Descriptions.Item>
        <Descriptions.Item label="事由">{{ detail.paymentReason }}</Descriptions.Item>
        <Descriptions.Item label="采购前置">{{ detail.purchaseSnapshot || detail.purchaseProcessInstanceId || '-' }}</Descriptions.Item>
        <Descriptions.Item label="租赁合同">{{ detail.leaseContractApplicationId || '-' }}</Descriptions.Item>
        <Descriptions.Item label="累计已支付">{{ detail.cumulativePaid }}</Descriptions.Item>
        <Descriptions.Item label="本次后累计">{{ detail.cumulativeAfter }}</Descriptions.Item>
        <Descriptions.Item label="支付日">{{ detail.actualPayDate || '-' }}</Descriptions.Item>
        <Descriptions.Item label="支付凭证">{{ detail.payVoucherUrl || '-' }}</Descriptions.Item>
      </Descriptions>
    </Spin>
  </Modal>
</template>
