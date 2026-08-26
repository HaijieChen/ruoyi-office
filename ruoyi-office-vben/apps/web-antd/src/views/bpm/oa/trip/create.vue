<script lang="ts" setup>
import type { BpmProcessInstanceApi } from '#/api/bpm/processInstance';

import { computed, nextTick, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import { confirm, Page } from '@vben/common-ui';
import { BpmNodeIdEnum } from '@vben/constants';
import { useTabs } from '@vben/hooks';
import { IconifyIcon } from '@vben/icons';

import { Button, Card, Col, message, Row, Space } from 'ant-design-vue';

import { getProcessDefinition } from '#/api/bpm/definition';
import { getApprovalDetail as getApprovalDetailApi } from '#/api/bpm/processInstance';
import { $t } from '#/locales';
import { router } from '#/router';
import ProcessInstanceTimeline from '#/views/bpm/processInstance/detail/modules/time-line.vue';

import FormBody from './modules/form-body.vue';

const { closeCurrentTab } = useTabs();
const { query } = useRoute();

const formLoading = ref(false);
const processTimeLineLoading = ref(false);
const formBodyRef = ref<InstanceType<typeof FormBody>>();
const processDefineKey = 'oa_business_trip';
const activityNodes = ref<BpmProcessInstanceApi.ApprovalNodeInfo[]>([]);
const processDefinitionId = ref('');
const resubmitSourceId = ref<number>();

const getTitle = computed(() =>
  resubmitSourceId.value ? '重新发起出差' : $t('ui.actionTitle.create', ['出差']),
);

async function onSubmit() {
  formLoading.value = true;
  try {
    await formBodyRef.value?.submit();
    await closeCurrentTab();
    await router.push({ name: 'OATripIndex' });
  } catch {
    /* validation already toasted */
  } finally {
    formLoading.value = false;
  }
}

function onBack() {
  confirm({
    content: '确定要返回上一页吗？请先保存您填写的信息！',
    icon: 'warning',
    beforeClose({ isConfirm }) {
      if (isConfirm) {
        closeCurrentTab();
      }
      return Promise.resolve(true);
    },
  });
}

async function getApprovalDetail(vars: Record<string, unknown> = {}) {
  if (!processDefinitionId.value) return;
  processTimeLineLoading.value = true;
  try {
    const data = await getApprovalDetailApi({
      processDefinitionId: processDefinitionId.value,
      activityId: BpmNodeIdEnum.START_USER_NODE_ID,
      processVariablesStr: JSON.stringify(vars),
    });
    if (data) activityNodes.value = data.activityNodes;
  } finally {
    processTimeLineLoading.value = false;
  }
}

onMounted(async () => {
  const processDefinitionDetail: any = await getProcessDefinition(undefined, processDefineKey);
  if (!processDefinitionDetail) {
    message.error('OA 出差的流程模型未配置，请检查！');
    return;
  }
  processDefinitionId.value = processDefinitionDetail.id;
  await nextTick();
  if (query.id) {
    resubmitSourceId.value = Number(query.id);
    await formBodyRef.value?.reset({ id: Number(query.id) });
  } else {
    await formBodyRef.value?.reset();
  }
  await getApprovalDetail(formBodyRef.value?.getPredictVariables?.() || {});
});
</script>

<template>
  <Page>
    <Row :gutter="16">
      <Col :span="16">
        <Card :title="getTitle" class="w-full" v-loading="formLoading">
          <template #extra>
            <Button type="default" @click="onBack">
              <IconifyIcon icon="lucide:arrow-left" />
              返回
            </Button>
          </template>
          <FormBody ref="formBodyRef" @predict-change="getApprovalDetail" @success="() => {}" />
          <template #actions>
            <Space warp :size="12" class="w-full px-6">
              <Button type="primary" @click="onSubmit" :loading="formLoading">提交</Button>
            </Space>
          </template>
        </Card>
      </Col>
      <Col :span="8">
        <Card title="流程" class="w-full" v-loading="processTimeLineLoading">
          <ProcessInstanceTimeline :activity-nodes="activityNodes" :show-status-icon="false" />
        </Card>
      </Col>
    </Row>
  </Page>
</template>
