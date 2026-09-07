<script lang="ts" setup>
import type { BpmOAOvertimeApi } from '#/api/bpm/oa/overtime';

import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import { ContentWrap } from '@vben/common-ui';

import { Spin } from 'ant-design-vue';

import { getOvertime } from '#/api/bpm/oa/overtime';
import { useDescription } from '#/components/description';

import { useDetailFormSchema } from './data';

const props = defineProps<{
  id: string;
}>();

const { query } = useRoute();

const loading = ref(false);
const formData = ref<BpmOAOvertimeApi.Overtime>();
const queryId = computed(() => query.id as string);

const [Descriptions] = useDescription({
  bordered: true,
  column: 1,
  class: 'mx-4',
  schema: useDetailFormSchema(),
});

/** 获取详情数据 */
async function getDetailData() {
  try {
    loading.value = true;
    formData.value = await getOvertime(Number(props.id || queryId.value));
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
