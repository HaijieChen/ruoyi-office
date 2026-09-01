<script lang="ts" setup>
import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Button, Descriptions, Spin } from 'ant-design-vue';

import { FilePreviewList } from '#/components/upload';
import { getPaymentApplication } from '#/api/finance/payment-application';
import type { FinancePaymentApplicationApi } from '#/api/finance/payment-application';
import { displayDate } from '#/utils/display-time';
import {
  financeProductLabel,
  financeReasonLabel,
  financeStatusLabel,
  financeTimingLabel,
} from '#/views/finance/shared/display-labels';
import PrintVoucher from './print-voucher.vue';
import PredocOverlay from './predoc-overlay.vue';

defineOptions({ name: 'FinancePaymentApplicationDetail' });

const detail = ref<FinancePaymentApplicationApi.Application | null>(null);
const loading = ref(false);
const predocOpen = ref(false);
const overlayKind = ref<'PURCHASE' | 'LEASE' | 'BUSINESS'>();

function openPredoc(kind: 'PURCHASE' | 'LEASE' | 'BUSINESS') {
  overlayKind.value = kind;
  predocOpen.value = true;
}

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
      <div v-if="detail" class="mb-3">
        <PrintVoucher :detail="detail" />
      </div>
      <Descriptions v-if="detail" bordered :column="1" size="small">
        <Descriptions.Item label="单号">{{ detail.applicationNo }}</Descriptions.Item>
        <Descriptions.Item label="标题">{{ detail.processTitle }}</Descriptions.Item>
        <Descriptions.Item label="状态">{{ financeStatusLabel(detail.status) }}</Descriptions.Item>
        <Descriptions.Item label="节点">{{ detail.currentNodeName }}</Descriptions.Item>
        <Descriptions.Item label="主体公司">
          {{ detail.entityCompanyName || '历史未记录' }}
        </Descriptions.Item>
        <Descriptions.Item label="收款方">{{ detail.payeeName }}</Descriptions.Item>
        <Descriptions.Item label="账户">
          {{ detail.payeeBankName }} / {{ detail.payeeBankAccount }}
        </Descriptions.Item>
        <Descriptions.Item label="金额">{{ detail.applyAmount }} {{ detail.currency }}</Descriptions.Item>
        <Descriptions.Item label="时效">{{ financeTimingLabel(detail.paymentTiming) }}</Descriptions.Item>
        <Descriptions.Item v-if="detail.paymentReason === 'BUSINESS'" label="产品类型">
          {{ financeProductLabel(detail.costProject) }}
        </Descriptions.Item>
        <Descriptions.Item label="事由">{{ financeReasonLabel(detail.paymentReason) }}</Descriptions.Item>
        <Descriptions.Item label="采购前置">
          <span>{{ detail.purchaseSnapshot || detail.purchaseProcessInstanceId || '-' }}</span>
          <Button
            v-if="detail.purchaseProcessInstanceId"
            type="link"
            class="px-1"
            @click="openPredoc('PURCHASE')"
          >
            查看采购申请
          </Button>
        </Descriptions.Item>
        <Descriptions.Item label="租赁合同">
          <span>{{ detail.leaseContractApplicationId || '-' }}</span>
          <Button
            v-if="detail.leaseContractApplicationId"
            type="link"
            class="px-1"
            @click="openPredoc('LEASE')"
          >
            查看租赁合同
          </Button>
        </Descriptions.Item>
        <Descriptions.Item v-if="detail.relatedContractApplicationId" label="合同签约">
          <span>{{ detail.relatedContractApplicationId }}</span>
          <Button type="link" class="px-1" @click="openPredoc('BUSINESS')">
            查看付款业务合同
          </Button>
        </Descriptions.Item>
        <Descriptions.Item label="累计已支付">{{ detail.cumulativePaid }}</Descriptions.Item>
        <Descriptions.Item label="本次后累计">{{ detail.cumulativeAfter }}</Descriptions.Item>
        <Descriptions.Item label="支付日">{{ displayDate(detail.actualPayDate) }}</Descriptions.Item>
        <Descriptions.Item label="已付合计">
          {{ detail.paidLineSum ?? 0 }}
        </Descriptions.Item>
        <Descriptions.Item label="支付凭证" :span="2">
          <FilePreviewList :value="detail.payVoucherUrl" />
        </Descriptions.Item>
        <Descriptions.Item v-if="detail.payLines?.length" label="历次回单">
          <div
            v-for="(line, i) in detail.payLines"
            :key="i"
            class="mb-2"
          >
            <div class="text-xs text-gray-500">
              {{ displayDate(line.actualPayDate) }} / {{ line.payAmount }}
            </div>
            <FilePreviewList :value="line.payVoucherUrl" />
          </div>
        </Descriptions.Item>
      </Descriptions>
    </Spin>
    <PredocOverlay
      v-model:open="predocOpen"
      :kind="overlayKind"
      :purchase-process-instance-id="detail?.purchaseProcessInstanceId"
      :purchase-snapshot="detail?.purchaseSnapshot"
      :contract-id="
        overlayKind === 'LEASE'
          ? detail?.leaseContractApplicationId
          : detail?.relatedContractApplicationId
      "
    />
  </Modal>
</template>
