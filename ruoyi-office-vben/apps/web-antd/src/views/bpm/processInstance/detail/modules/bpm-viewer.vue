<script setup lang="ts">
import { nextTick, ref, watch } from 'vue';

import { MyProcessViewer } from '#/views/bpm/components/bpmn-process-designer/package';

defineOptions({ name: 'ProcessInstanceBpmnViewer' });

const props = withDefaults(
  defineProps<{
    bpmnXml?: string;
    loading?: boolean; // 是否加载中
    modelView?: Object;
  }>(),
  {
    loading: false,
    modelView: () => ({}),
    bpmnXml: '',
  },
);

// BPMN 流程图数据
const view = ref({
  bpmnXml: '',
});
const viewerRef = ref<{ refreshViewport?: () => void } | null>(null);

/** 监控 modelView 更新 */
watch(
  () => props.modelView,
  async (newModelView) => {
    if (newModelView) {
      // @ts-ignore
      view.value = newModelView;
      await nextTick();
      viewerRef.value?.refreshViewport?.();
    }
  },
  { immediate: true, deep: true },
);

/** 监听 bpmnXml */
watch(
  () => props.bpmnXml,
  (value) => {
    view.value.bpmnXml = value;
  },
);

function refreshViewport() {
  viewerRef.value?.refreshViewport?.();
}

defineExpose({ refreshViewport });
</script>

<template>
  <div
    v-loading="loading"
    class="h-full w-full min-h-[520px] overflow-auto rounded-lg border border-gray-200 bg-white p-4"
  >
    <MyProcessViewer
      ref="viewerRef"
      key="processViewer"
      :xml="view.bpmnXml"
      :view="view"
      class="h-full min-h-[500px] w-full"
    />
  </div>
</template>
