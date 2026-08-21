<script lang="ts" setup>
import { onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { downloadFileFromBlobPart } from '@vben/utils';

import { Button, InputNumber, Space, Table, message } from 'ant-design-vue';

import {
  exportPayrollBatch,
  generatePayrollBatch,
  getPayrollBatch,
  listPayrollLines,
  publishPayrollBatch,
  withdrawPayrollBatch,
  type PayrollBatchApi,
} from '#/api/hrm/payroll/batch';

defineOptions({ name: 'HrmPayrollBatch' });

const yearMonth = ref(202608);
const status = ref('DRAFT');
const lines = ref<PayrollBatchApi.Line[]>([]);
const loading = ref(false);

async function reload() {
  loading.value = true;
  try {
    const batch = await getPayrollBatch(yearMonth.value);
    status.value = batch?.status ?? 'DRAFT';
    lines.value = (await listPayrollLines(yearMonth.value)) ?? [];
  } finally {
    loading.value = false;
  }
}

async function onGenerate() {
  await generatePayrollBatch(yearMonth.value);
  message.success('已生成草稿');
  await reload();
}

async function onPublish() {
  await publishPayrollBatch(yearMonth.value);
  message.success('已下发');
  await reload();
}

async function onWithdraw() {
  await withdrawPayrollBatch(yearMonth.value);
  message.success('已撤回');
  await reload();
}

async function onExport() {
  const data = await exportPayrollBatch(yearMonth.value);
  downloadFileFromBlobPart({ fileName: `${yearMonth.value}工资表.xls`, source: data });
}

onMounted(reload);
</script>

<template>
  <Page title="月度工资核算">
    <Space class="mb-4">
      <InputNumber v-model:value="yearMonth" :min="202001" />
      <span>状态：{{ status }}</span>
      <Button type="primary" @click="onGenerate">生成草稿</Button>
      <Button @click="onPublish">确认下发</Button>
      <Button @click="onWithdraw">撤回</Button>
      <Button @click="onExport">导出</Button>
      <Button @click="reload">刷新</Button>
    </Space>
    <Table
      :data-source="lines"
      :loading="loading"
      row-key="id"
      :columns="[
        { title: '姓名', dataIndex: 'employeeName' },
        { title: '应付', dataIndex: 'payable' },
        { title: '加班', dataIndex: 'overtime' },
        { title: '个税', dataIndex: 'tax' },
        { title: '实发', dataIndex: 'net' },
      ]"
    />
  </Page>
</template>
