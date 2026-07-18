<script lang="ts" setup>
import type { BpmFormDataSourceApi } from '#/api/bpm/form-data-source';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Button, message, Popconfirm, Table, Tag } from 'ant-design-vue';

import {
  getDataSourceVersionList,
  publishDataSource,
} from '#/api/bpm/form-data-source';

const emit = defineEmits(['success']);
const source = ref<BpmFormDataSourceApi.Definition>();
const versions = ref<BpmFormDataSourceApi.VersionSummary[]>([]);
const publishingId = ref<number>();

const columns = [
  { dataIndex: 'version', key: 'version', title: '版本' },
  { dataIndex: 'status', key: 'status', title: '状态' },
  { dataIndex: 'createTime', key: 'createTime', title: '创建时间' },
  { dataIndex: 'updateTime', key: 'updateTime', title: '更新时间' },
  { key: 'action', title: '操作', width: 100 },
];

async function loadVersions() {
  if (!source.value) return;
  versions.value = await getDataSourceVersionList(source.value.id);
}

async function handlePublish(record: Record<string, unknown>) {
  const row = record as unknown as BpmFormDataSourceApi.VersionSummary;
  if (!source.value) return;
  publishingId.value = row.id;
  try {
    await publishDataSource(source.value.id, row.id);
    message.success(`V${row.version} 已发布`);
    await loadVersions();
    emit('success');
  } finally {
    publishingId.value = undefined;
  }
}

const [Modal, modalApi] = useVbenModal({
  footer: false,
  async onOpenChange(open) {
    if (!open) {
      source.value = undefined;
      versions.value = [];
      return;
    }
    const row = modalApi.getData<BpmFormDataSourceApi.Definition>();
    if (!row?.id) return;
    source.value = row;
    modalApi.lock();
    try {
      await loadVersions();
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal :title="`${source?.name ?? ''} - 版本历史`" class="w-2/3">
    <div class="px-5 pb-5">
      <p class="mb-3 text-sm text-gray-500">
        此处仅展示版本元数据，不加载 SQL、参数 Schema 或结果
        Schema。完整配置仅在编辑器中按更新权限读取。
      </p>
      <Table
        :columns="columns"
        :data-source="versions"
        :pagination="false"
        row-key="id"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'version'">
            V{{ record.version }}
          </template>
          <template v-else-if="column.key === 'status'">
            <Tag :color="record.status === 1 ? 'orange' : 'green'">
              {{ record.status === 1 ? '草稿' : '已发布' }}
            </Tag>
          </template>
          <template v-else-if="column.key === 'action' && record.status === 1">
            <Popconfirm
              title="发布后该版本将不可修改，确认继续？"
              @confirm="handlePublish(record)"
            >
              <Button
                v-access:code="['bpm:form-data-source:publish']"
                :loading="publishingId === record.id"
                size="small"
                type="link"
              >
                发布
              </Button>
            </Popconfirm>
          </template>
        </template>
      </Table>
    </div>
  </Modal>
</template>
