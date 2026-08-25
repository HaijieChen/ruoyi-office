<script lang="ts" setup>
import { h, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import { Button, Table } from 'ant-design-vue';

import { listMyPayslips, type PayrollBatchApi } from '#/api/hrm/payroll/batch';

defineOptions({ name: 'HrmPayrollPayslip' });

const lines = ref<PayrollBatchApi.Line[]>([]);
const loading = ref(false);
const revealed = ref<Set<number>>(new Set());

function isOpen(record: PayrollBatchApi.Line) {
  return record.id != null && revealed.value.has(record.id);
}

function toggle(record: PayrollBatchApi.Line) {
  if (record.id == null) {
    return;
  }
  const next = new Set(revealed.value);
  if (next.has(record.id)) {
    next.delete(record.id);
  } else {
    next.add(record.id);
  }
  revealed.value = next;
}

function masked(record: PayrollBatchApi.Line, value: unknown) {
  if (isOpen(record)) {
    return value ?? '';
  }
  return '****';
}

function col(title: string, dataIndex: string, width: number, fixed?: 'left' | 'right') {
  return {
    title: () => h('span', { style: { whiteSpace: 'nowrap' } }, title),
    dataIndex,
    width,
    fixed,
    customRender: ({ record, text }: { record: PayrollBatchApi.Line; text: unknown }) =>
      dataIndex === 'employeeName' || dataIndex === 'yearMonth' ? (text ?? '') : masked(record, text),
  };
}

const columns = [
  {
    title: '',
    dataIndex: '_eye',
    width: 56,
    fixed: 'left' as const,
    customRender: ({ record }: { record: PayrollBatchApi.Line }) =>
      h(
        Button,
        {
          type: 'text',
          size: 'small',
          title: isOpen(record) ? '隐藏明细' : '显示明细',
          onClick: () => toggle(record),
        },
        {
          default: () =>
            h(IconifyIcon, {
              icon: isOpen(record) ? 'lucide:eye' : 'lucide:eye-off',
              class: 'size-4',
            }),
        },
      ),
  },
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
  col('病假扣除', 'sickPay', 90),
  col('事假/缺勤天数', 'personalAbsenceDays', 130),
  col('事假扣除', 'personalLeavePay', 90),
  col('出差补贴', 'tripSubsidy', 90),
  col('其它加减', 'otherAdjust', 90),
  col('应付工资', 'payable', 90),
  col('社保扣除', 'socialDeduct', 90),
  col('公积金扣除', 'housingDeduct', 100),
  col('个人所得税', 'tax', 110),
  col('实发工资', 'net', 110, 'right'),
];

onMounted(async () => {
  loading.value = true;
  try {
    lines.value = (await listMyPayslips()) ?? [];
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <Page title="我的工资条">
    <p class="mb-3 text-sm text-gray-500">默认隐藏明细，点击左侧眼睛查看该月数据。</p>
    <Table
      class="payroll-line-table"
      :data-source="lines"
      :loading="loading"
      row-key="id"
      :scroll="{ x: 3260 }"
      :columns="columns"
    />
  </Page>
</template>

<style scoped>
.payroll-line-table :deep(.ant-table-thead > tr > th) {
  white-space: nowrap;
}
</style>
