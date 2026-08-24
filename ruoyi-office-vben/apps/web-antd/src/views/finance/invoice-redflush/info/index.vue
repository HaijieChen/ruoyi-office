<script lang="ts" setup>
import { onMounted, ref } from 'vue';

import { Descriptions, DescriptionsItem, Modal, Spin } from 'ant-design-vue';

import { requestClient } from '#/api/request';

import InvoiceInfo from '../../invoice-application/info/index.vue';

defineOptions({ name: 'FinanceInvoiceRedflushInfo' });

const props = defineProps<{
  id?: number | string;
}>();

const loading = ref(false);
const detail = ref<Record<string, any> | null>(null);
const originOpen = ref(false);

async function load() {
  if (props.id == null || props.id === '') return;
  loading.value = true;
  try {
    detail.value = await requestClient.get('/finance/invoice-redflush/get', {
      params: { id: props.id },
    });
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <Spin :spinning="loading">
    <Descriptions v-if="detail" bordered size="small" :column="2">
      <DescriptionsItem label="红冲单号">{{ detail.applicationNo }}</DescriptionsItem>
      <DescriptionsItem label="审批状态">{{ detail.approvalStatus }}</DescriptionsItem>
      <DescriptionsItem label="红冲原因">{{ detail.reason }}</DescriptionsItem>
      <DescriptionsItem label="金额">{{ detail.totalAmount }}</DescriptionsItem>
      <DescriptionsItem label="前置开票申请">
        <a @click="originOpen = true">#{{ detail.predecessorApplicationId }}</a>
      </DescriptionsItem>
    </Descriptions>
  </Spin>
  <Modal v-model:open="originOpen" title="原开票申请" width="800px" :footer="null">
    <InvoiceInfo
      v-if="detail?.predecessorApplicationId"
      :id="String(detail.predecessorApplicationId)"
    />
  </Modal>
</template>
