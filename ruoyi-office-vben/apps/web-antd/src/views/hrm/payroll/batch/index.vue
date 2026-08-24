<script lang="ts" setup>
import { onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { downloadFileFromBlobPart } from '@vben/utils';

import {
  Button,
  Form,
  FormItem,
  InputNumber,
  Radio,
  RadioGroup,
  Space,
  Table,
  Upload,
  message,
} from 'ant-design-vue';

import {
  downloadPunchTemplate,
  exportPayrollBatch,
  generatePayrollBatch,
  getPayrollBatch,
  listPayrollLines,
  publishPayrollBatch,
  uploadPayrollPunch,
  withdrawPayrollBatch,
  type PayrollBatchApi,
} from '#/api/hrm/payroll/batch';
import {
  createMinWage,
  getMinWageHistory,
  type MinWageApi,
} from '#/api/hrm/payroll/min-wage';

defineOptions({ name: 'HrmPayrollBatch' });

const yearMonth = ref(202608);
const status = ref('DRAFT');
const lines = ref<PayrollBatchApi.Line[]>([]);
const loading = ref(false);
const unmatched = ref<string[]>([]);

const minWageAmount = ref<number | null>(null);
const minWageNextMonth = ref(false);
const minWageRows = ref<MinWageApi.MinWageRow[]>([]);
const minWageLoading = ref(false);

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

async function loadMinWage() {
  minWageLoading.value = true;
  try {
    minWageRows.value = (await getMinWageHistory()) ?? [];
  } finally {
    minWageLoading.value = false;
  }
}

async function onSaveMinWage() {
  if (minWageAmount.value == null) {
    message.warning('请输入最低工资');
    return;
  }
  await createMinWage({ amount: minWageAmount.value, nextMonth: minWageNextMonth.value });
  message.success('最低工资已保存');
  minWageAmount.value = null;
  minWageNextMonth.value = false;
  await loadMinWage();
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

async function onDownloadTemplate() {
  const data = await downloadPunchTemplate();
  downloadFileFromBlobPart({ fileName: '打卡导入模板.xls', source: data });
}

function onUploadPunch(file: File) {
  void (async () => {
    const result = await uploadPayrollPunch(yearMonth.value, file);
    unmatched.value = result?.unmatched ?? [];
    message.success(`打卡已合并，匹配 ${result?.matched ?? 0} 人`);
    await reload();
  })();
  return false;
}

onMounted(async () => {
  await Promise.all([reload(), loadMinWage()]);
});
</script>

<template>
  <Page title="月度工资核算">
    <Form layout="inline" class="mb-4">
      <FormItem label="最低工资">
        <InputNumber v-model:value="minWageAmount" :min="0" :precision="2" />
      </FormItem>
      <FormItem label="生效">
        <RadioGroup v-model:value="minWageNextMonth">
          <Radio :value="false">当月</Radio>
          <Radio :value="true">下月</Radio>
        </RadioGroup>
      </FormItem>
      <FormItem>
        <Button @click="onSaveMinWage">保存最低工资</Button>
      </FormItem>
    </Form>
    <Table
      class="mb-6"
      size="small"
      :data-source="minWageRows"
      :loading="minWageLoading"
      row-key="id"
      :pagination="{ pageSize: 5 }"
      :columns="[
        { title: '生效年月', dataIndex: 'effectiveMonth' },
        { title: '金额', dataIndex: 'amount' },
        { title: '录入时间', dataIndex: 'createTime' },
      ]"
    />

    <Space class="mb-4">
      <InputNumber v-model:value="yearMonth" :min="202001" />
      <span>状态：{{ status }}</span>
      <Button type="primary" @click="onGenerate">生成草稿</Button>
      <Button @click="onDownloadTemplate">下载打卡模板</Button>
      <Upload :show-upload-list="false" :before-upload="onUploadPunch" accept=".xls,.xlsx">
        <Button>上传打卡并合并</Button>
      </Upload>
      <Button @click="onPublish">确认下发</Button>
      <Button @click="onWithdraw">撤回</Button>
      <Button @click="onExport">导出</Button>
      <Button @click="reload">刷新</Button>
    </Space>
    <p v-if="unmatched.length" class="mb-2">未匹配打卡姓名：{{ unmatched.join('、') }}</p>
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
