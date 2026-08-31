<script lang="ts" setup>
import { computed } from 'vue';

import { Modal } from 'ant-design-vue';

import OutingDetail from '#/views/bpm/oa/outing/detail.vue';
import TripDetail from '#/views/bpm/oa/trip/detail.vue';

defineOptions({ name: 'FinanceExpensePredocOverlay' });

const open = defineModel<boolean>('open', { default: false });

const props = defineProps<{
  predocType?: string;
  billId?: number | string;
}>();

const title = computed(() =>
  props.predocType === 'OUTING' ? '外出申请' : '出差申请',
);

const id = computed(() =>
  props.billId == null || props.billId === '' ? '' : String(props.billId),
);
</script>

<template>
  <Modal v-model:open="open" :title="title" width="800px" :footer="null">
    <div v-if="!id">无法加载前置单据</div>
    <TripDetail v-else-if="predocType === 'TRIP'" :id="id" embedded />
    <OutingDetail v-else-if="predocType === 'OUTING'" :id="id" embedded />
    <div v-else>无法加载前置单据</div>
  </Modal>
</template>
