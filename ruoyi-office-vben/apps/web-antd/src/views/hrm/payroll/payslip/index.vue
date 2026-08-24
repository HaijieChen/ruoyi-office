<script lang="ts" setup>
import { h, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Table } from 'ant-design-vue';

import { listMyPayslips, type PayrollBatchApi } from '#/api/hrm/payroll/batch';

defineOptions({ name: 'HrmPayrollPayslip' });

const lines = ref<PayrollBatchApi.Line[]>([]);
const loading = ref(false);

function col(title: string, dataIndex: string, width: number, fixed?: 'left' | 'right') {
  return {
    title: () => h('span', { style: { whiteSpace: 'nowrap' } }, title),
    dataIndex,
    width,
    fixed,
  };
}

const columns = [
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
    <Table
      class="payroll-line-table"
      :data-source="lines"
      :loading="loading"
      row-key="id"
      :scroll="{ x: 3200 }"
      :columns="columns"
    />
  </Page>
</template>

<style scoped>
.payroll-line-table :deep(.ant-table-thead > tr > th) {
  white-space: nowrap;
}
</style>
