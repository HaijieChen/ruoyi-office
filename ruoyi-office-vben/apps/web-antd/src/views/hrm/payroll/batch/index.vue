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
      :scroll="{ x: 3600 }"
      :columns="[
        { title: '年月', dataIndex: 'yearMonth', width: 90 },
        { title: '公司', dataIndex: 'companyName', width: 100 },
        { title: '部门', dataIndex: 'deptName', width: 100 },
        { title: '岗位', dataIndex: 'jobPost', width: 100 },
        { title: '姓名', dataIndex: 'employeeName', width: 90, fixed: 'left' },
        { title: '入职日期', dataIndex: 'entryDate', width: 110 },
        { title: '工资', dataIndex: 'wage', width: 90 },
        { title: '社保基数', dataIndex: 'socialBase', width: 90 },
        { title: '公积金基数', dataIndex: 'housingBase', width: 100 },
        { title: '全勤奖', dataIndex: 'fullAttendanceBonus', width: 80 },
        { title: '住房补贴', dataIndex: 'housingSubsidy', width: 90 },
        { title: '绩效', dataIndex: 'performance', width: 80 },
        { title: '奖金', dataIndex: 'bonus', width: 80 },
        { title: '补贴', dataIndex: 'subsidy', width: 80 },
        { title: '法定节假日加班', dataIndex: 'holidayOvertimeDays', width: 120 },
        { title: '法定节假日加班费', dataIndex: 'holidayOvertimePay', width: 130 },
        { title: '工作日加班补贴', dataIndex: 'weekdayOvertimePay', width: 120 },
        { title: '病假天数', dataIndex: 'sickDays', width: 90 },
        { title: '病假系数', dataIndex: 'sickRate', width: 90 },
        { title: '病假工资', dataIndex: 'sickPay', width: 90 },
        { title: '事假/缺勤天数', dataIndex: 'personalAbsenceDays', width: 120 },
        { title: '事假工资', dataIndex: 'personalLeavePay', width: 90 },
        { title: '出差补贴', dataIndex: 'tripSubsidy', width: 90 },
        { title: '其它加减', dataIndex: 'otherAdjust', width: 90 },
        { title: '应付工资', dataIndex: 'payable', width: 90 },
        { title: '社保扣除', dataIndex: 'socialDeduct', width: 90 },
        { title: '公积金扣除', dataIndex: 'housingDeduct', width: 100 },
        { title: '个人所得税', dataIndex: 'tax', width: 100 },
        { title: '实发工资', dataIndex: 'net', width: 90, fixed: 'right' },
        { title: '银行卡号', dataIndex: 'bankAccount', width: 160 },
        { title: '开户支行', dataIndex: 'bankName', width: 140 },
        { title: '手机号码', dataIndex: 'mobile', width: 120 },
        { title: '身份证号码', dataIndex: 'idCard', width: 170 },
      ]"
    />
  </Page>
</template>
