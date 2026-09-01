<script lang="ts" setup>
/**
 * Read-only approval overview for list detail (and optional reuse).
 * Loads chain + diagram + task records via BPM APIs (T2-A).
 */
import type { BpmProcessInstanceApi } from '#/api/bpm/processInstance';

import { computed, ref, watch } from 'vue';

import { BpmModelType } from '@vben/constants';

import { Alert, Empty, Spin, TabPane, Tabs } from 'ant-design-vue';

import {
  getApprovalDetail,
  getProcessInstanceBpmnModelView,
} from '#/api/bpm/processInstance';

import ProcessInstanceBpmnViewer from './bpm-viewer.vue';
import ProcessInstanceSimpleViewer from './simple-bpm-viewer.vue';
import BpmProcessInstanceTaskList from './task-list.vue';
import ProcessInstanceTimeline from './time-line.vue';

defineOptions({ name: 'ApprovalOverviewPanel' });

const props = defineProps<{
  /** Current process instance id; empty → empty state, no API calls */
  processInstanceId?: null | string;
}>();

const loading = ref(false);
/** Detail/timeline load failure (product-critical) */
const errorMsg = ref<string>('');
/** Diagram-only failure (partial degrade) */
const diagramErrorMsg = ref<string>('');
const activityNodes = ref<BpmProcessInstanceApi.ApprovalNodeInfo[]>([]);
const processModelView = ref<any>({});
const modelType = ref<number | undefined>();
const activeTab = ref('progress');
const taskListRef = ref<InstanceType<typeof BpmProcessInstanceTaskList>>();
const bpmnViewerRef = ref<{ refreshViewport?: () => void } | null>(null);
const diagramRenderKey = ref(0);

const hasId = computed(
  () => !!props.processInstanceId && String(props.processInstanceId).trim() !== '',
);

/** requestClient throws business body `{ code, msg }`, not Axios error */
function resolveApiErrorMsg(error: any, fallback: string) {
  return (
    error?.msg ||
    error?.message ||
    error?.response?.data?.msg ||
    fallback
  );
}

async function load() {
  errorMsg.value = '';
  diagramErrorMsg.value = '';
  activityNodes.value = [];
  processModelView.value = {};
  modelType.value = undefined;

  if (!hasId.value) {
    loading.value = false;
    return;
  }

  const id = String(props.processInstanceId);
  loading.value = true;
  try {
    // Decouple timeline from diagram so one failure does not wipe the other
    const [detailResult, modelResult] = await Promise.allSettled([
      getApprovalDetail({ processInstanceId: id }),
      getProcessInstanceBpmnModelView(id),
    ]);

    if (detailResult.status === 'fulfilled') {
      const detail = detailResult.value;
      activityNodes.value = detail?.activityNodes || [];
      modelType.value = detail?.processDefinition?.modelType;
    } else {
      activityNodes.value = [];
      const msg = resolveApiErrorMsg(
        detailResult.reason,
        '加载审批进度失败',
      );
      // 已结束实例偶发查不到时，显示空状态而不是红字
      errorMsg.value = msg.includes('流程实例不存在') ? '' : msg;
    }

    if (modelResult.status === 'fulfilled') {
      if (modelResult.value) {
        processModelView.value = modelResult.value;
      }
    } else {
      diagramErrorMsg.value = resolveApiErrorMsg(
        modelResult.reason,
        '加载流程图失败',
      );
    }
  } finally {
    loading.value = false;
  }
}

watch(
  () => props.processInstanceId,
  () => {
    void load();
  },
  { immediate: true },
);

watch(activeTab, (key) => {
  if (key === 'record' && hasId.value) {
    taskListRef.value?.refresh?.();
  }
  if (key === 'diagram') {
    diagramRenderKey.value += 1;
    requestAnimationFrame(() => {
      bpmnViewerRef.value?.refreshViewport?.();
    });
  }
});
</script>

<template>
  <div class="approval-overview-panel">
    <div v-if="!hasId" class="py-6">
      <Empty description="暂无审批进度" />
    </div>
    <Spin v-else :spinning="loading">
      <Alert
        v-if="errorMsg"
        type="error"
        show-icon
        class="mb-3"
        :message="errorMsg"
      />
      <Tabs v-model:active-key="activeTab" size="small">
        <TabPane key="progress" tab="审批进度">
          <div class="min-h-[120px]">
            <ProcessInstanceTimeline
              v-if="activityNodes.length"
              :activity-nodes="activityNodes"
            />
            <Empty
              v-else-if="!loading && !errorMsg"
              description="暂无节点数据"
            />
          </div>
        </TabPane>
        <TabPane key="diagram" tab="流程图">
          <div class="min-h-[520px]">
            <Alert
              v-if="diagramErrorMsg"
              type="warning"
              show-icon
              class="mb-3"
              :message="diagramErrorMsg"
            />
            <template v-else-if="activeTab === 'diagram'">
              <ProcessInstanceSimpleViewer
                v-if="modelType === BpmModelType.SIMPLE"
                :key="`simple-${processInstanceId}-${diagramRenderKey}`"
                :loading="loading"
                :model-view="processModelView"
              />
              <ProcessInstanceBpmnViewer
                v-else
                ref="bpmnViewerRef"
                :key="`bpmn-${processInstanceId}-${diagramRenderKey}`"
                :loading="loading"
                :model-view="processModelView"
              />
            </template>
          </div>
        </TabPane>
        <TabPane key="record" tab="流转记录">
          <BpmProcessInstanceTaskList
            v-if="hasId"
            ref="taskListRef"
            :id="String(processInstanceId)"
            :loading="loading"
          />
        </TabPane>
      </Tabs>
    </Spin>
  </div>
</template>
