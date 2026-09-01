<script lang="ts" setup>
import type { BpmOATripApi } from '#/api/bpm/oa/trip';

import { computed, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { ContentWrap } from '@vben/common-ui';

import { Divider, Spin } from 'ant-design-vue';

import { getTrip } from '#/api/bpm/oa/trip';
import { useDescription } from '#/components/description';
import ApprovalOverviewPanel from '#/views/bpm/processInstance/detail/modules/approval-overview-panel.vue';

import { useDetailFormSchema } from './data';

defineOptions({ name: 'OATripDetail' });

const props = defineProps<{
  activityNodes?: any[];
  embedded?: boolean;
  id?: string;
  processInstance?: any;
}>();

const { query } = useRoute();

const loading = ref(false);
const formData = ref<BpmOATripApi.Trip>();
const queryId = computed(() => query.id as string);

const [Descriptions] = useDescription({
  bordered: true,
  column: 1,
  class: 'mx-4',
  schema: useDetailFormSchema(),
});

const showOverview = computed(
  () => !props.processInstance && !!formData.value?.processInstanceId,
);

async function getDetailData() {
  try {
    loading.value = true;
    formData.value = await getTrip(Number(props.id || queryId.value));
  } catch {
    formData.value = undefined;
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  getDetailData();
});

watch(
  () => props.id,
  () => {
    if (props.embedded) {
      void getDetailData();
    }
  },
);
</script>

<template>
  <div v-if="embedded">
    <Spin :spinning="loading" tip="加载中...">
      <div v-if="!formData && !loading">无法加载前置单据</div>
      <Descriptions v-else :data="formData" />
    </Spin>
  </div>
  <ContentWrap v-else class="m-2">
    <Spin :spinning="loading" tip="加载中...">
      <Descriptions :data="formData" />
      <template v-if="showOverview">
        <Divider orientation="left" class="!mt-6">审批全貌</Divider>
        <ApprovalOverviewPanel
          :process-instance-id="formData?.processInstanceId"
        />
      </template>
    </Spin>
  </ContentWrap>
</template>
