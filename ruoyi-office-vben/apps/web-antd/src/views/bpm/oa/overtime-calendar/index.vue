<script lang="ts" setup>
import type { BpmOAOvertimeCalendarApi } from '#/api/bpm/oa/overtime-calendar';

import { onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Button, Card, Space, Table, Tag, message } from 'ant-design-vue';

import {
  fetchOvertimeCalendar,
  getOvertimeCalendarPage,
  verifyOvertimeCalendar,
} from '#/api/bpm/oa/overtime-calendar';

defineOptions({ name: 'BpmOAOvertimeCalendar' });

const loading = ref(false);
const rows = ref<BpmOAOvertimeCalendarApi.Version[]>([]);

async function load() {
  loading.value = true;
  try {
    const page = await getOvertimeCalendarPage({ pageNo: 1, pageSize: 50 });
    rows.value = page?.list || [];
  } finally {
    loading.value = false;
  }
}

async function onFetch() {
  const result = await fetchOvertimeCalendar();
  message.success(String(result || '已触发抓取'));
  await load();
}

async function onVerify(row: BpmOAOvertimeCalendarApi.Version, enable: boolean) {
  if (!row.id) {
    return;
  }
  await verifyOvertimeCalendar(row.id, enable);
  message.success(enable ? '已启用' : '已驳回');
  await load();
}

const columns = [
  { title: '年份', dataIndex: 'calendarYear', width: 80 },
  { title: '状态', dataIndex: 'status', width: 120 },
  { title: '来源', dataIndex: 'source' },
  { title: 'URL', dataIndex: 'sourceUrl', ellipsis: true },
  { title: '抓取时间', dataIndex: 'fetchedAt', width: 180 },
  { title: '说明', dataIndex: 'parseNote', ellipsis: true },
  { title: '差异', dataIndex: 'diffJson', ellipsis: true },
  { title: '操作', key: 'action', width: 180 },
];

onMounted(load);
</script>

<template>
  <Page auto-content-height>
    <Card title="加班节假日日历">
      <template #extra>
        <Space>
          <Button @click="load">刷新</Button>
          <Button type="primary" @click="onFetch">立即抓取</Button>
        </Space>
      </template>
      <p class="mb-3 text-sm text-gray-500">
        业务申请只读已核验 ACTIVE
        版本。抓取结果进入待核验，不会自动覆盖。核验启用不改写历史加班单。
      </p>
      <Table
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        :pagination="false"
        row-key="id"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'status'">
            <Tag
              :color="
                record.status === 'ACTIVE'
                  ? 'green'
                  : record.status === 'PENDING'
                    ? 'gold'
                    : 'default'
              "
            >
              {{ record.status }}
            </Tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <Space v-if="record.status === 'PENDING'">
              <Button type="link" @click="onVerify(record, true)">启用</Button>
              <Button type="link" danger @click="onVerify(record, false)">
                驳回
              </Button>
            </Space>
          </template>
        </template>
      </Table>
    </Card>
  </Page>
</template>
