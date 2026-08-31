<script lang="ts" setup>
import type { BpmOAOutingApi } from '#/api/bpm/oa/outing';

import { computed, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { ContentWrap } from '@vben/common-ui';

import { Spin } from 'ant-design-vue';

import { getOuting } from '#/api/bpm/oa/outing';
import { useDescription } from '#/components/description';

import { useDetailFormSchema } from './data';

const props = defineProps<{
  id?: string;
  embedded?: boolean;
}>();

const { query } = useRoute();

const loading = ref(false);
const formData = ref<BpmOAOutingApi.Outing>();
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
    formData.value = await getOuting(Number(props.id || queryId.value));
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
    </Spin>
  </ContentWrap>
</template>
