<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { EmployeeArchiveApi } from '#/api/hrm/employee';

import { onActivated, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page, useVbenModal } from '@vben/common-ui';
import { downloadFileFromBlobPart, isEmpty } from '@vben/utils';

import { Modal, message } from 'ant-design-vue';
import type { SystemDeptApi } from '#/api/system/dept';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteEmployeeArchive,
  deleteEmployeeArchiveList,
  exportEmployeeArchiveExcel,
  getEmployeeArchivePage,
  generateUserForEmployee,
  batchGenerateUserForEmployee,
} from '#/api/hrm/employee';
import { $t } from '#/locales';

import { DeptSelectModal } from '#/views/system/dept/components';

import { useGridColumns, useGridFormSchema } from './data';
import ImportModal from './modules/import-modal.vue';
import GenerateRoleModal from './modules/generate-role-modal.vue';

defineOptions({ name: 'HrmEmployeeArchiveList' });

const router = useRouter();

// 选中的记录ID列表
const checkedIds = ref<number[]>([]);

// 部门选择弹窗引用
const deptSelectModalRef = ref<InstanceType<typeof DeptSelectModal>>();

const [ImportModalComp, importModalApi] = useVbenModal({
  connectedComponent: ImportModal,
});

const [GenerateRoleModalComp, generateRoleModalApi] = useVbenModal({
  connectedComponent: GenerateRoleModal,
});

function pickRoles(): Promise<number[] | null> {
  return new Promise((resolve) => {
    generateRoleModalApi.setData({ resolve, settled: false });
    generateRoleModalApi.open();
  });
}

/** 刷新表格 */
function onRefresh() {
  gridApi.query();
}

/** 新增员工档案 */
function handleCreate() {
  router.push({
    path: '/hrm/employee/employee-archive-info',
    query: {
      t: Date.now(), // 添加时间戳作为随机串
    },
  });
}

/** 编辑员工档案 */
function handleEdit(row: EmployeeArchiveApi.EmployeeArchive) {
  router.push({
    path: '/hrm/employee/employee-archive-info',
    query: {
      id: row.id,
      t: Date.now(),
    },
  });
}

/** 删除员工档案 */
async function handleDelete(row: EmployeeArchiveApi.EmployeeArchive) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.name]),
    key: 'action_key_msg',
  });
  try {
    await deleteEmployeeArchive(row.id as number);
    message.success({
      content: $t('ui.actionMessage.deleteSuccess', [row.name]),
      key: 'action_key_msg',
    });
    onRefresh();
  } finally {
    hideLoading();
  }
}

/** 批量删除员工档案 */
async function handleDeleteBatch() {
  if (isEmpty(checkedIds.value)) {
    message.warning('请先选择要删除的记录');
    return;
  }

  const hideLoading = message.loading('正在删除...', 0);
  try {
    await deleteEmployeeArchiveList(checkedIds.value);
    message.success('批量删除成功');
    checkedIds.value = [];
    onRefresh();
  } catch (error) {
    message.error('批量删除失败');
  } finally {
    hideLoading();
  }
}

/** 导出 Excel */
async function handleExport() {
  const hideLoading = message.loading('正在导出...', 0);
  try {
    const data = await exportEmployeeArchiveExcel(await gridApi.formApi.getValues());
    downloadFileFromBlobPart({ fileName: '文枢花名册.xlsx', source: data });
    message.success('导出成功');
  } catch (error) {
    message.error('导出失败');
  } finally {
    hideLoading();
  }
}

/** 导入文枢花名册 */
function handleImport() {
  importModalApi.open();
}

/** 生成用户 */
async function handleGenerateUser(row: EmployeeArchiveApi.EmployeeArchive) {
  if (row.userGenerated) {
    message.warning('该员工已生成用户，无需重复生成');
    return;
  }
  const roleIds = await pickRoles();
  if (roleIds === null) {
    return;
  }
  const hideLoading = message.loading('正在生成用户...', 0);
  try {
    await generateUserForEmployee(row.id as number, roleIds);
    message.success('生成用户成功');
    onRefresh();
  } catch (error) {
    message.error('生成用户失败');
  } finally {
    hideLoading();
  }
}

function pendingGenerateIds(rows: EmployeeArchiveApi.EmployeeArchive[]) {
  return rows
    .filter((item) => !item.userGenerated && item.id)
    .map((item) => item.id as number);
}

async function confirmSkipGenerated(skipped: number, pending: number) {
  if (skipped <= 0) {
    return true;
  }
  return new Promise<boolean>((resolve) => {
    Modal.confirm({
      title: `将跳过 ${skipped} 名已生成账号的员工，为 ${pending} 名生成？`,
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    });
  });
}

/** 当前筛选下尚未生成账号的员工 */
async function collectPendingUserIds() {
  const formValues = await gridApi.formApi.getValues();
  const queryParams: Record<string, any> = { ...formValues };
  if (!queryParams.deptId || queryParams.deptId === '') {
    delete queryParams.deptId;
  }
  if (!queryParams.deptName || queryParams.deptName === '') {
    delete queryParams.deptId;
    delete queryParams.deptName;
  }
  const { list } = await getEmployeeArchivePage({
    pageNo: 1,
    pageSize: 500,
    ...queryParams,
  });
  return list
    .filter((item) => !item.userGenerated && item.id)
    .map((item) => item.id as number);
}

async function doBatchGenerateUser(ids: number[]) {
  const roleIds = await pickRoles();
  if (roleIds === null) {
    return;
  }
  const hideLoading = message.loading('正在批量生成账号...', 0);
  try {
    await batchGenerateUserForEmployee(ids, roleIds);
    message.success(`已生成 ${ids.length} 个登录账号`);
    checkedIds.value = [];
    onRefresh();
  } catch (error) {
    message.error('批量生成账号失败');
  } finally {
    hideLoading();
  }
}

/** 批量生成用户：勾选不禁用；提交时丢掉已有账号。未勾选则按筛选未生成批量生成 */
async function handleBatchGenerateUser() {
  const records = (gridApi.grid.getCheckboxRecords() ??
    []) as EmployeeArchiveApi.EmployeeArchive[];
  if (!isEmpty(checkedIds.value) || !isEmpty(records)) {
    const checkedPending = pendingGenerateIds(records);
    const skipped = records.length - checkedPending.length;
    if (isEmpty(checkedPending)) {
      message.warning(
        isEmpty(records) && isEmpty(checkedIds.value)
          ? '请先选择要生成用户的记录'
          : '所选员工均已生成账号',
      );
      return;
    }
    if (!(await confirmSkipGenerated(skipped, checkedPending.length))) {
      return;
    }
    await doBatchGenerateUser(checkedPending);
    return;
  }
  const ids = await collectPendingUserIds();
  if (isEmpty(ids)) {
    message.info('没有需要生成账号的员工');
    return;
  }
  Modal.confirm({
    title: `将为 ${ids.length} 名尚未生成账号的员工创建登录账号（工号即用户名）？`,
    onOk: () => doBatchGenerateUser(ids),
  });
}

/** 复选框变化事件 */
function handleRowCheckboxChange() {
  const records = gridApi.grid.getCheckboxRecords();
  checkedIds.value = records.map((item: EmployeeArchiveApi.EmployeeArchive) => item.id as number);
}

/** 部门选择处理 */
function handleDeptSelect(dept: SystemDeptApi.Dept & { companyName?: string }) {
  // 设置部门ID和部门名称
  gridApi.formApi.setFieldValue('deptId', dept.id);
  gridApi.formApi.setFieldValue('deptName', dept.name);
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useGridFormSchema(deptSelectModalRef),
    wrapperClass: 'grid-cols-4',
    collapsed: true,
  },
  gridOptions: {
    columns: useGridColumns(),
    height: 'auto',
    pagerConfig: {
      enabled: true,
    },
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          // 过滤掉空的 deptId，避免清空后仍然传递参数
          const queryParams = { ...formValues };
          if (!queryParams.deptId || queryParams.deptId === '') {
            delete queryParams.deptId;
          }
          // 如果 deptName 为空，也删除 deptId
          if (!queryParams.deptName || queryParams.deptName === '') {
            delete queryParams.deptId;
            delete queryParams.deptName;
          }
          return await getEmployeeArchivePage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...queryParams,
          });
        },
      },
    },
    rowConfig: {
      keyField: 'id',
      isHover: true,
    },
    toolbarConfig: {
      refresh: { code: 'query' },
      search: true,
    },
  } as VxeTableGridOptions<EmployeeArchiveApi.EmployeeArchive>,
  gridEvents: {
    checkboxAll: handleRowCheckboxChange,
    checkboxChange: handleRowCheckboxChange,
  },
});

// 页签切换时自动刷新表格数据
onActivated(() => {
  onRefresh();
});
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="员工档案列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create'),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['hrm:employee-archive:create'],
              onClick: handleCreate,
            },
            {
              label: $t('ui.actionTitle.export'),
              type: 'primary',
              icon: ACTION_ICON.DOWNLOAD,
              auth: ['hrm:employee-archive:export'],
              onClick: handleExport,
            },
            {
              label: '导入',
              type: 'primary',
              icon: ACTION_ICON.UPLOAD,
              auth: ['hrm:employee-archive:create'],
              onClick: handleImport,
            },
            {
              label: '生成账号',
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['hrm:employee-archive:create'],
              onClick: handleBatchGenerateUser,
            },
            {
              label: $t('ui.actionTitle.deleteBatch'),
              type: 'primary',
              danger: true,
              icon: ACTION_ICON.DELETE,
              disabled: isEmpty(checkedIds),
              auth: ['hrm:employee-archive:delete'],
              onClick: handleDeleteBatch,
            },
          ]"
        />
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.edit'),
              icon: ACTION_ICON.EDIT,
              auth: ['hrm:employee-archive:update'],
              onClick: () => handleEdit(row),
            },
            {
              label: '生成用户',
              type: 'link',
              icon: ACTION_ICON.ADD,
              disabled: row.userGenerated,
              auth: ['hrm:employee-archive:create'],
              onClick: () => handleGenerateUser(row),
            },
            {
              label: $t('ui.actionTitle.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              popConfirm: {
                title: $t('ui.actionMessage.deleteConfirm', [row.name]),
                confirm: () => handleDelete(row),
              },
              auth: ['hrm:employee-archive:delete'],
            },
          ]"
        />
      </template>
    </Grid>
    <!-- 部门/公司选择弹窗（列表筛选允许选公司，后端展开为公司下员工） -->
    <DeptSelectModal
      ref="deptSelectModalRef"
      :allow-company="true"
      @select="handleDeptSelect"
    />
    <ImportModalComp @success="onRefresh" />
    <GenerateRoleModalComp />
  </Page>
</template>

