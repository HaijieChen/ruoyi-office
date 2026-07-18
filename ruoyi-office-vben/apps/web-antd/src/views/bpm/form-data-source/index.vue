<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { BpmFormDataSourceApi } from '#/api/bpm/form-data-source';

import { Page, useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  disableDataSource,
  getDataSourcePage,
} from '#/api/bpm/form-data-source';

import { useGridColumns, useGridFormSchema } from './data';
import Editor from './modules/editor.vue';
import TrialRun from './modules/trial-run.vue';
import VersionList from './modules/version-list.vue';

defineOptions({ name: 'BpmFormDataSource' });

const [EditorModal, editorModalApi] = useVbenModal({
  connectedComponent: Editor,
  destroyOnClose: true,
});
const [TrialRunModal, trialRunModalApi] = useVbenModal({
  connectedComponent: TrialRun,
  destroyOnClose: true,
});
const [VersionListModal, versionListModalApi] = useVbenModal({
  connectedComponent: VersionList,
  destroyOnClose: true,
});

function handleRefresh() {
  gridApi.query();
}

function handleCreate() {
  editorModalApi.setData(null).open();
}

function handleEdit(row: BpmFormDataSourceApi.Definition) {
  editorModalApi.setData(row).open();
}

function handleTrialRun(row: BpmFormDataSourceApi.Definition) {
  trialRunModalApi.setData(row).open();
}

function handleVersions(row: BpmFormDataSourceApi.Definition) {
  versionListModalApi.setData(row).open();
}

async function handleDisable(row: BpmFormDataSourceApi.Definition) {
  await disableDataSource(row.id);
  message.success(`${row.name} 已停用`);
  handleRefresh();
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: { schema: useGridFormSchema() },
  gridOptions: {
    columns: useGridColumns(),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) =>
          await getDataSourcePage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
          }),
      },
    },
    rowConfig: { isHover: true, keyField: 'id' },
    toolbarConfig: { refresh: true, search: true },
  } as VxeTableGridOptions<BpmFormDataSourceApi.Definition>,
});
</script>

<template>
  <Page auto-content-height>
    <EditorModal @success="handleRefresh" />
    <TrialRunModal />
    <VersionListModal @success="handleRefresh" />
    <Grid table-title="表单数据源">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              auth: ['bpm:form-data-source:create'],
              icon: ACTION_ICON.ADD,
              label: '新增数据源',
              onClick: handleCreate,
              type: 'primary',
            },
          ]"
        />
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              auth: ['bpm:form-data-source:update'],
              icon: ACTION_ICON.EDIT,
              label: '编辑草稿',
              onClick: handleEdit.bind(null, row),
              type: 'link',
            },
            {
              auth: ['bpm:form-data-source:update'],
              label: '试运行',
              onClick: handleTrialRun.bind(null, row),
              type: 'link',
            },
            {
              auth: ['bpm:form-data-source:query'],
              icon: ACTION_ICON.VIEW,
              label: '版本历史',
              onClick: handleVersions.bind(null, row),
              type: 'link',
            },
            {
              auth: ['bpm:form-data-source:update'],
              danger: true,
              disabled: row.status !== 0,
              label: '停用',
              popConfirm: {
                confirm: handleDisable.bind(null, row),
                title: `确认停用 ${row.name}？已发布版本会保留，但运行时不可再执行。`,
              },
              type: 'link',
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
