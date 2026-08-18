<script lang="ts" setup>
import type { BpmOATripApi } from '#/api/bpm/oa/trip';
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
import { createTrip, getTrip } from '#/api/bpm/oa/trip';
import { getApprovalDetail as getApprovalDetailApi } from '#/api/bpm/processInstance';
import { $t } from '#/locales';
import { router } from '#/router';
import ProcessInstanceTimeline from '#/views/bpm/processInstance/detail/modules/time-line.vue';

import { calcTripHours, useFormSchema } from './data';

const { closeCurrentTab } = useTabs();
const { query } = useRoute();
const userStore = useUserStore();

const formLoading = ref(false);
const processTimeLineLoading = ref(false);

const processDefineKey = 'oa_business_trip';
const activityNodes = ref<BpmProcessInstanceApi.ApprovalNodeInfo[]>([]);
const processDefinitionId = ref('');
const resubmitSourceId = ref<number>();

const getTitle = computed(() => {
  return resubmitSourceId.value
    ? '重新发起出差'
    : $t('ui.actionTitle.create', ['出差']);
});

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    formItemClass: 'col-span-2',
    labelWidth: 120,
  },
  layout: 'horizontal',
  schema: useFormSchema(),
  showDefaultActions: false,
});

function applyApplicantFromLogin() {
  return formApi.setValues({
    userNickname: userStore.userInfo?.nickname || '',
    deptName: userStore.userInfo?.deptName || '',
  });
}

/** 提交申请：始终 POST create */
async function onSubmit() {
  const { valid } = await formApi.validate();
  if (!valid) {
    return;
  }
  const data = (await formApi.getValues()) as BpmOATripApi.Trip;
  const hours = calcTripHours(data.startTime, data.endTime);
  if (hours == null) {
    message.warning('结束时间必须晚于开始时间，且时长须大于 0');
    return;
  }
  const submitData: BpmOATripApi.TripCreate = {
    type: Number(data.type),
    startTime: Number(data.startTime),
    endTime: Number(data.endTime),
  };
  try {
    formLoading.value = true;
    await createTrip(submitData);
    message.success($t('ui.actionMessage.operationSuccess'));
    await closeCurrentTab();
    await router.push({
      name: 'OATripIndex',
    });
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

async function getApprovalDetail() {
  if (!processDefinitionId.value) {
    return;
  }
  processTimeLineLoading.value = true;
  try {
    const values = (await formApi.getValues()) as Partial<BpmOATripApi.Trip>;
    const hours = calcTripHours(values.startTime, values.endTime);
    const data = await getApprovalDetailApi({
      processDefinitionId: processDefinitionId.value,
      activityId: BpmNodeIdEnum.START_USER_NODE_ID,
      processVariablesStr: JSON.stringify({
        hours,
        type: values.type,
      }),
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

/** 重提：只带业务字段，不带 id/status/processInstanceId */
async function getDetail(id: number) {
  try {
    formLoading.value = true;
    const data = await getTrip(id);
    if (!data) {
      message.error('重新发起出差失败，原因：出差数据不存在');
      return;
    }
    if (data.status !== BpmProcessInstanceStatus.REJECT) {
      message.error('仅驳回单据可以重提');
      return;
    }
    resubmitSourceId.value = data.id;
    await formApi.setValues({
      type: data.type,
      startTime: data.startTime,
      endTime: data.endTime,
      hours: calcTripHours(data.startTime, data.endTime),
      userNickname: userStore.userInfo?.nickname || '',
      deptName: userStore.userInfo?.deptName || '',
    });
  } finally {
    formLoading.value = false;
  }
}

onMounted(async () => {
  await applyApplicantFromLogin();
  const processDefinitionDetail: any = await getProcessDefinition(
    undefined,
    processDefineKey,
  );
  if (!processDefinitionDetail) {
    message.error('OA 出差的流程模型未配置，请检查！');
    return;
  }
  processDefinitionId.value = processDefinitionDetail.id;

  if (query.id) {
    await getDetail(Number(query.id));
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
            <Space warp :size="12" class="w-full px-6">
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
