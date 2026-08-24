<script lang="ts" setup>
import { onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Table } from 'ant-design-vue';

import { listMyPayslips, type PayrollBatchApi } from '#/api/hrm/payroll/batch';

defineOptions({ name: 'HrmPayrollPayslip' });

const lines = ref<PayrollBatchApi.Line[]>([]);
const loading = ref(false);

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
      :data-source="lines"
      :loading="loading"
      row-key="id"
      :scroll="{ x: 2800 }"
      :columns="[
        { title: '年月', dataIndex: 'yearMonth', width: 90 },
        { title: '公司', dataIndex: 'companyName', width: 100 },
        { title: '部门', dataIndex: 'deptName', width: 100 },
        { title: '岗位', dataIndex: 'jobPost', width: 100 },
        { title: '姓名', dataIndex: 'employeeName', width: 90 },
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
        { title: '实发工资', dataIndex: 'net', width: 90 },
      ]"
    />
  </Page>
</template>
