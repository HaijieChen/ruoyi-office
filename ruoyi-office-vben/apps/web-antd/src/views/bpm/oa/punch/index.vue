<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { BpmOAPunchApi } from '#/api/bpm/oa/punch';

import { h, onActivated } from 'vue';

import { Page, prompt } from '@vben/common-ui';
import { BpmProcessInstanceStatus } from '@vben/constants';

import { message, Textarea } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { getPunchPage } from '#/api/bpm/oa/punch';
import { cancelProcessInstanceByStartUser } from '#/api/bpm/processInstance';
import { $t } from '#/locales';
import { router } from '#/router';

import { useGridColumns, useGridFormSchema } from './data';

/** Grid 槽位 row 是宽记录；列表动作只用 id/流程/状态，不把整行当成必填 Punch */
type PunchActionRow = {
  id?: number;
  processInstanceId?: string;
  status?: number;
};

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 发起补卡 */
function handleCreate() {
  router.push({
    name: 'OAPunchCreate',
  });
}

/** 驳回重提：打开创建页回填业务字段，提交走 POST create */
function handleReCreate(row: PunchActionRow) {
  router.push({
    name: 'OAPunchCreate',
    query: {
      id: row.id,
    },
  });
}

/** 取消补卡 */
function handleCancel(row: PunchActionRow) {
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
        await cancelProcessInstanceByStartUser(
          row.processInstanceId,
          scope.value,
        );
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

/** 查看补卡详情 */
function handleDetail(row: PunchActionRow) {
  router.push({
    name: 'OAPunchDetail',
    query: { id: row.id },
  });
}

/** 审批进度 */
function handleProgress(row: PunchActionRow) {
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
          return await getPunchPage({
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
  } as VxeTableGridOptions<BpmOAPunchApi.Punch>,
});

onActivated(() => {
  handleRefresh();
});
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="补卡列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: '发起补卡',
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
