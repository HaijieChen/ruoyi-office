<script lang="ts" setup>
import { h, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { downloadFileFromBlobPart } from '@vben/utils';

import { Button, Card, InputNumber, Radio, RadioGroup, Space, Table, Upload, message } from 'ant-design-vue';

import {
  adjustPayrollLine,
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
const minWageWhen = ref<'current' | 'next'>('current');
const minWageRows = ref<MinWageApi.MinWageRow[]>([]);
const minWageLoading = ref(false);

const editableKeys = new Set([
  'housingSubsidy',
  'performance',
  'bonus',
  'subsidy',
  'holidayOvertimeDays',
  'holidayOvertimePay',
  'weekdayOvertimePay',
  'tripSubsidy',
  'otherAdjust',
  'socialDeduct',
  'housingDeduct',
  'tax',
]);

async function saveCell(record: PayrollBatchApi.Line, key: string, value: number | null) {
  if (!record.id || status.value !== 'DRAFT') {
    return;
  }
  (record as Record<string, unknown>)[key] = value;
  await adjustPayrollLine(record.id, { [key]: value });
  await reload();
}

function col(
  title: string,
  dataIndex: string,
  width = 110,
  fixed?: 'left' | 'right',
) {
  return {
    title: () => h('span', { style: { whiteSpace: 'nowrap' } }, title),
    dataIndex,
    width,
    fixed,
    customRender: ({ record }: { record: PayrollBatchApi.Line }) => {
      const current = (record as Record<string, unknown>)[dataIndex];
      if (!editableKeys.has(dataIndex) || status.value !== 'DRAFT') {
        return current ?? '';
      }
      return h(InputNumber, {
        size: 'small',
        value: current as number,
        style: { width: '100%' },
        onChange: (v: number | string | null) => {
          (record as Record<string, unknown>)[dataIndex] = v;
        },
        onBlur: () => {
          void saveCell(record, dataIndex, (record as Record<string, unknown>)[dataIndex] as number);
        },
      });
    },
  };
}

const lineColumns = [
  col('姓名', 'employeeName', 120, 'left'),
  col('年月', 'yearMonth', 90),
  col('公司', 'companyName', 80),
  col('部门', 'deptName', 80),
  col('岗位', 'jobPost', 80),
  col('入职日期', 'entryDate', 110),
  col('工资', 'wage', 90),
  col('社保基数', 'socialBase', 90),
  col('公积金基数', 'housingBase', 100),
  col('全勤奖', 'fullAttendanceBonus', 80),
  col('住房补贴', 'housingSubsidy', 90),
  col('绩效', 'performance', 80),
  col('奖金', 'bonus', 80),
  col('补贴', 'subsidy', 80),
  col('法定节假日加班', 'holidayOvertimeDays', 130),
  col('法定节假日加班费', 'holidayOvertimePay', 140),
  col('工作日加班补贴', 'weekdayOvertimePay', 130),
  col('病假天数', 'sickDays', 90),
  col('病假系数', 'sickRate', 90),
  col('病假工资', 'sickPay', 90),
  col('事假/缺勤天数', 'personalAbsenceDays', 130),
  col('事假工资', 'personalLeavePay', 90),
  col('出差补贴', 'tripSubsidy', 90),
  col('其它加减', 'otherAdjust', 90),
  col('应付工资', 'payable', 90),
  col('社保扣除', 'socialDeduct', 90),
  col('公积金扣除', 'housingDeduct', 100),
  col('个人所得税', 'tax', 110),
  col('银行卡号', 'bankAccount', 160),
  col('开户支行', 'bankName', 140),
  col('手机号码', 'mobile', 120),
  col('身份证号码', 'idCard', 170),
  col('实发工资', 'net', 110, 'right'),
];

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
  await createMinWage({
    amount: minWageAmount.value,
    nextMonth: minWageWhen.value === 'next',
  });
  message.success('最低工资已保存');
  minWageAmount.value = null;
  minWageWhen.value = 'current';
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
  <Page>
    <Card size="small" title="最低工资" class="mb-4">
      <Space wrap>
        <span>金额</span>
        <InputNumber v-model:value="minWageAmount" :min="0" :precision="2" />
        <span>生效</span>
        <RadioGroup v-model:value="minWageWhen">
          <Radio value="current">当月</Radio>
          <Radio value="next">下月</Radio>
        </RadioGroup>
        <Button @click="onSaveMinWage">保存最低工资</Button>
      </Space>
      <Table
        class="mt-3"
        size="small"
        :data-source="minWageRows"
        :loading="minWageLoading"
        row-key="id"
        :pagination="false"
        :columns="[
          { title: '生效年月', dataIndex: 'effectiveMonth' },
          { title: '金额', dataIndex: 'amount' },
          { title: '录入时间', dataIndex: 'createTime' },
        ]"
      />
    </Card>

    <Card size="small" title="月度核算">
      <div class="mb-4 bg-white" style="position: relative; z-index: 5">
        <Space wrap>
          <span>年月</span>
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
        <p v-if="unmatched.length" class="mt-2 mb-0">未匹配打卡姓名：{{ unmatched.join('、') }}</p>
        <p v-if="status === 'DRAFT'" class="mt-2 mb-0 text-gray-500">草稿可点单元格改补贴/加班/个税等，失焦保存并重算应付与实发。</p>
      </div>
      <Table
        class="payroll-line-table"
        size="small"
        :data-source="lines"
        :loading="loading"
        row-key="id"
        :pagination="false"
        :scroll="{ x: 3600 }"
        :columns="lineColumns"
      />
    </Card>
  </Page>
</template>

<style scoped>
.payroll-line-table :deep(.ant-table-thead > tr > th) {
  white-space: nowrap;
}
</style>
