<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { BpmOATripApi } from '#/api/bpm/oa/trip';

import { h, onActivated } from 'vue';

import { Page, prompt } from '@vben/common-ui';
import { BpmProcessInstanceStatus } from '@vben/constants';

import { message, Textarea } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { getTripPage } from '#/api/bpm/oa/trip';
import { cancelProcessInstanceByStartUser } from '#/api/bpm/processInstance';
import { $t } from '#/locales';
import { router } from '#/router';

import { useGridColumns, useGridFormSchema } from './data';

function handleRefresh() {
  gridApi.query();
}

function handleCreate() {
  router.push({
    name: 'OATripCreate',
  });
}

function handleReCreate(row: BpmOATripApi.Trip) {
  router.push({
    name: 'OATripCreate',
    query: {
      id: row.id,
    },
  });
}

function handleCancel(row: BpmOATripApi.Trip) {
  prompt({
    title: '取消流程',
    content: '请输入取消原因',
    modelPropName: 'value',
    component: () => {
      return h(Textarea, {
        placeholder: '请输入取消原因',
        allowClear: true,
        rows: 2,
        rules: [{ required: true, message: '请输入取消原因' }],
      });
    },
    async beforeClose(scope) {
      if (!scope.isConfirm) {
        return;
      }
      if (!scope.value) {
        message.error('请输入取消原因');
        return false;
      }
      if (!row.processInstanceId) {
        message.error('缺少流程实例，无法取消');
        return false;
      }
      const hideLoading = message.loading({
        content: '正在取消中...',
        duration: 0,
      });
      try {
        await cancelProcessInstanceByStartUser(row.processInstanceId, scope.value);
        message.success('取消成功');
        handleRefresh();
      } catch {
        return false;
      } finally {
        hideLoading();
      }
    },
  });
}

function handleDetail(row: BpmOATripApi.Trip) {
  router.push({
    name: 'OATripDetail',
    query: { id: row.id },
  });
}

function handleProgress(row: BpmOATripApi.Trip) {
  router.push({
    name: 'BpmProcessInstanceDetail',
    query: { id: row.processInstanceId },
  });
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useGridFormSchema(),
  },
  gridOptions: {
    columns: useGridColumns(),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          return await getTripPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
          });
        },
      },
    },
    rowConfig: {
      keyField: 'id',
      isHover: true,
    },
    toolbarConfig: {
      refresh: true,
      search: true,
    },
  } as VxeTableGridOptions<BpmOATripApi.Trip>,
});

onActivated(() => {
  handleRefresh();
});
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="出差列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: '发起出差',
              type: 'primary',
              icon: ACTION_ICON.ADD,
              onClick: handleCreate,
            },
          ]"
        />
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.detail'),
              type: 'link',
              icon: ACTION_ICON.VIEW,
              onClick: handleDetail.bind(null, row),
            },
            {
              label: '审批进度',
              type: 'link',
              icon: ACTION_ICON.VIEW,
              onClick: handleProgress.bind(null, row),
            },
            {
              label: '取消',
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              ifShow: row.status === BpmProcessInstanceStatus.RUNNING,
              onClick: handleCancel.bind(null, row),
            },
            {
              label: '重提',
              type: 'link',
              icon: ACTION_ICON.ADD,
              ifShow: row.status === BpmProcessInstanceStatus.REJECT,
              onClick: handleReCreate.bind(null, row),
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
