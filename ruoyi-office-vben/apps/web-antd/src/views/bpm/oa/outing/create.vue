<script lang="ts" setup>
import type { BpmOAOutingApi } from '#/api/bpm/oa/outing';
import type { BpmProcessInstanceApi } from '#/api/bpm/processInstance';

import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import { confirm, Page, useVbenForm } from '@vben/common-ui';
import { BpmNodeIdEnum, BpmProcessInstanceStatus } from '@vben/constants';
import { useTabs } from '@vben/hooks';
import { IconifyIcon } from '@vben/icons';
import { useUserStore } from '@vben/stores';

import { Button, Card, Col, message, Row, Space } from 'ant-design-vue';

import { getProcessDefinition } from '#/api/bpm/definition';
import { createOuting, getOuting } from '#/api/bpm/oa/outing';
import { getApprovalDetail as getApprovalDetailApi } from '#/api/bpm/processInstance';
import { $t } from '#/locales';
import { router } from '#/router';
import ProcessInstanceTimeline from '#/views/bpm/processInstance/detail/modules/time-line.vue';

import { calcOutingHours, useFormSchema } from './data';

const { closeCurrentTab } = useTabs();
const { query } = useRoute();
const userStore = useUserStore();

const formLoading = ref(false);
const processTimeLineLoading = ref(false);
const processDefineKey = 'oa_outing';
const activityNodes = ref<BpmProcessInstanceApi.ApprovalNodeInfo[]>([]);
const processDefinitionId = ref('');
const isResubmit = ref(false);

const getTitle = computed(() => {
  return isResubmit.value ? '重提外出' : $t('ui.actionTitle.create', ['外出']);
});

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    formItemClass: 'col-span-2',
    labelWidth: 140,
  },
  layout: 'horizontal',
  schema: useFormSchema(),
  showDefaultActions: false,
});

function applyCurrentApplicant() {
  return formApi.setValues({
    userNickname: userStore.userInfo?.nickname || '',
    deptName: userStore.userInfo?.deptName || '',
  });
}

function buildCreatePayload(
  values: Record<string, any>,
): BpmOAOutingApi.OutingCreate {
  const payload: BpmOAOutingApi.OutingCreate = {
    reason: values.reason,
    location: values.location,
    startTime: Number(values.startTime),
    endTime: Number(values.endTime),
  };
  if (
    values.needOutput !== undefined &&
    values.needOutput !== null &&
    values.needOutput !== ''
  ) {
    payload.needOutput = values.needOutput;
  }
  if (Array.isArray(values.attachmentUrls) && values.attachmentUrls.length > 0) {
    payload.attachmentUrls = values.attachmentUrls;
  }
  return payload;
}

/** 提交申请：始终 POST create，不带 id/status/processInstanceId */
async function onSubmit() {
  const { valid } = await formApi.validate();
  if (!valid) {
    return;
  }
  const values = (await formApi.getValues()) as Record<string, any>;
  if (calcOutingHours(values.startTime, values.endTime) === undefined) {
    message.warning('结束时间必须晚于开始时间，且时长须大于 0');
    return;
  }
  try {
    formLoading.value = true;
    await createOuting(buildCreatePayload(values));
    message.success($t('ui.actionMessage.operationSuccess'));
    await closeCurrentTab();
    await router.push({
      name: 'OAOutingIndex',
    });
  } finally {
    formLoading.value = false;
  }
}

/** 返回上一页 */
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

/** 预览审批链，不收集发起人自选审批人 */
async function getApprovalDetail() {
  if (!processDefinitionId.value) {
    return;
  }
  processTimeLineLoading.value = true;
  try {
    const values = (await formApi.getValues()) as Record<string, any>;
    const hours = calcOutingHours(values.startTime, values.endTime);
    const processVariables: Record<string, unknown> = {};
    if (hours !== undefined) {
      processVariables.hours = hours;
    }
    if (
      values.needOutput !== undefined &&
      values.needOutput !== null &&
      values.needOutput !== ''
    ) {
      processVariables.need_output = values.needOutput;
    }
    const data = await getApprovalDetailApi({
      processDefinitionId: processDefinitionId.value,
      activityId: BpmNodeIdEnum.START_USER_NODE_ID,
      processVariablesStr: JSON.stringify(processVariables),
    });
    if (!data) {
      message.error('查询不到审批详情信息！');
      return;
    }
    activityNodes.value = data.activityNodes;
  } finally {
    processTimeLineLoading.value = false;
  }
}

/** 驳回重提：只回填业务字段，不含 id/status/processInstanceId */
async function prefillRejected(id: number) {
  try {
    formLoading.value = true;
    const data = await getOuting(id);
    if (!data) {
      message.error('重提外出失败，原因：外出数据不存在');
      return;
    }
    if (data.status !== BpmProcessInstanceStatus.REJECT) {
      message.error('仅驳回的外出单可以重提');
      return;
    }
    isResubmit.value = true;
    await formApi.setValues({
      reason: data.reason,
      location: data.location,
      startTime: data.startTime,
      endTime: data.endTime,
      hours: calcOutingHours(data.startTime, data.endTime),
      needOutput: data.needOutput,
      attachmentUrls: data.attachmentUrls,
    });
  } finally {
    formLoading.value = false;
  }
}

onMounted(async () => {
  await applyCurrentApplicant();
  const processDefinitionDetail = await getProcessDefinition(
    undefined,
    processDefineKey,
  );
  if (!processDefinitionDetail) {
    message.error('OA 外出的流程模型未配置，请检查！');
    return;
  }
  processDefinitionId.value = processDefinitionDetail.id;

  if (query.id) {
    await prefillRejected(Number(query.id));
  }

  await getApprovalDetail();
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

          <Form />
          <template #actions>
            <Space wrap :size="12" class="w-full px-6">
              <Button type="primary" @click="onSubmit" :loading="formLoading">
                提交
              </Button>
            </Space>
          </template>
        </Card>
      </Col>
      <Col :span="8">
        <Card title="流程" class="w-full" v-loading="processTimeLineLoading">
          <ProcessInstanceTimeline
            :activity-nodes="activityNodes"
            :show-status-icon="false"
          />
        </Card>
      </Col>
    </Row>
  </Page>
</template>
