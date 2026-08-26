<script lang="ts" setup>
import type { FinanceCompanyApproverApi } from '#/api/finance/company-approver';

import { onMounted, ref } from 'vue';

import { Page, useVbenModal } from '@vben/common-ui';

import { Button, Table } from 'ant-design-vue';

import { getCompanyApproverList } from '#/api/finance/company-approver';

import FormModal from './modules/form.vue';

defineOptions({ name: 'FinanceCompanyApprover' });

const loading = ref(false);
const rows = ref<FinanceCompanyApproverApi.Item[]>([]);

const [AssignModal, assignModalApi] = useVbenModal({
  connectedComponent: FormModal,
  destroyOnClose: true,
});

async function load() {
  loading.value = true;
  try {
    rows.value = (await getCompanyApproverList()) || [];
  } finally {
    loading.value = false;
  }
}

function handleAssign(row: FinanceCompanyApproverApi.Item) {
  assignModalApi.setData({ ...row });
  assignModalApi.open();
}

onMounted(() => {
  void load();
});
</script>

<template>
  <Page auto-content-height>
    <AssignModal @success="load" />
    <Table
      :columns="[
        { title: '主体公司', dataIndex: 'entityCompanyName', key: 'c' },
        {
          title: '财务审批人',
          dataIndex: 'userNames',
          key: 'u',
          customRender: ({ text }: { text?: string[] }) =>
            text?.length ? text.join('、') : '未分配',
        },
        { title: '操作', key: 'action', width: 100 },
      ]"
      :data-source="rows"
      :loading="loading"
      :pagination="false"
      row-key="entityCompanyDeptId"
      size="small"
    >
      <template #title>公司财务审批人</template>
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'action'">
          <Button type="link" @click="handleAssign(record)">分配</Button>
        </template>
      </template>
    </Table>
  </Page>
</template>
