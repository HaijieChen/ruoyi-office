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
