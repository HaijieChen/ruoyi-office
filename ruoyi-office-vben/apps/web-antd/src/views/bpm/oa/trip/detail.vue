<script lang="ts" setup>
import type { BpmOATripApi } from '#/api/bpm/oa/trip';

import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import { ContentWrap } from '@vben/common-ui';

import { Spin } from 'ant-design-vue';

import { getTrip } from '#/api/bpm/oa/trip';
import { useDescription } from '#/components/description';

import { useDetailFormSchema } from './data';

const props = defineProps<{
  id: string;
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

async function getDetailData() {
  try {
    loading.value = true;
    formData.value = await getTrip(Number(props.id || queryId.value));
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  getDetailData();
});
</script>

<template>
  <ContentWrap class="m-2">
    <Spin :spinning="loading" tip="加载中...">
      <Descriptions :data="formData" />
    </Spin>
  </ContentWrap>
</template>
