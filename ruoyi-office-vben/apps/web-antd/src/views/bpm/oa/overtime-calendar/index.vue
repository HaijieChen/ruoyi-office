<script lang="ts" setup>
import type { BpmOAOvertimeCalendarApi } from '#/api/bpm/oa/overtime-calendar';

import { computed, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { useAccess } from '@vben/access';

import {
  Alert,
  Button,
  Card,
  Select,
  Space,
  Table,
  Tag,
  message,
} from 'ant-design-vue';

import {
  fetchOvertimeCalendar,
  getOvertimeCalendarPage,
  verifyOvertimeCalendar,
} from '#/api/bpm/oa/overtime-calendar';

import {
  buildDayRows,
  isSeedVersion,
  statusLabel,
} from './calendar-view';

defineOptions({ name: 'BpmOAOvertimeCalendar' });

const { hasAccessByCodes } = useAccess();
const canVerify = computed(() =>
  hasAccessByCodes(['bpm:oa-overtime-calendar:verify']),
);

const loading = ref(false);
const rows = ref<BpmOAOvertimeCalendarApi.Version[]>([]);
const year = ref(2026);
const kindFilter = ref<'LEGAL' | 'SPECIAL' | 'ALL'>('LEGAL');

const years = computed(() => {
  const set = new Set<number>([2026, 2027, new Date().getFullYear()]);
  for (const row of rows.value) {
    if (row.calendarYear) {
      set.add(row.calendarYear);
    }
  }
  return [...set].sort();
});

const yearRows = computed(() =>
  rows.value.filter((row) => row.calendarYear === year.value),
);

const activeRow = computed(
  () => yearRows.value.find((row) => row.status === 'ACTIVE') || null,
);

const pendingRows = computed(() =>
  yearRows.value.filter((row) => row.status === 'PENDING'),
);

const failedRows = computed(() =>
  yearRows.value.filter((row) => row.status === 'FAILED'),
);

const unpublishedRows = computed(() =>
  yearRows.value.filter((row) => row.status === 'NOT_PUBLISHED'),
);

const dayRows = computed(() =>
  activeRow.value ? buildDayRows(activeRow.value, year.value) : [],
);

const visibleDays = computed(() => {
  if (kindFilter.value === 'ALL') {
    return dayRows.value;
  }
  if (kindFilter.value === 'SPECIAL') {
    return dayRows.value.filter((row) => row.kind !== 'WEEKDAY');
  }
  return dayRows.value.filter((row) => row.kind === 'LEGAL_HOLIDAY');
});

const legalCount = computed(
  () => dayRows.value.filter((row) => row.kind === 'LEGAL_HOLIDAY').length,
);

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

const versionColumns = [
  { title: '状态', dataIndex: 'status', width: 140 },
  { title: '来源', dataIndex: 'source' },
  { title: '抓取/导入时间', dataIndex: 'fetchedAt', width: 180 },
  { title: '说明', dataIndex: 'parseNote', ellipsis: true },
  { title: '差异', dataIndex: 'diffJson', ellipsis: true },
  { title: '操作', key: 'action', width: 160 },
];

const dayColumns = [
  { title: '日期', dataIndex: 'date', width: 120 },
  { title: '星期', dataIndex: 'weekday', width: 80 },
  { title: '节日', dataIndex: 'festival', width: 100 },
  { title: '分类', dataIndex: 'kindLabel', width: 120 },
  { title: '可申请加班', dataIndex: 'allowed', width: 120 },
];

onMounted(load);
</script>

<template>
  <Page auto-content-height>
    <Card title="加班节假日日历">
      <template #extra>
        <Space>
          <Select
            v-model:value="year"
            :options="years.map((item) => ({ label: `${item}年`, value: item }))"
            style="width: 120px"
          />
          <Button @click="load">刷新</Button>
          <Button v-if="canVerify" type="primary" @click="onFetch">
            立即抓取
          </Button>
        </Space>
      </template>

      <Alert
        v-if="activeRow && isSeedVersion(activeRow)"
        class="mb-3"
        type="info"
        show-icon
        message="当前生效的是初始化已核验版本，不是本次在线采集结果。"
      />
      <Alert
        v-else-if="!activeRow && failedRows.length"
        class="mb-3"
        type="error"
        show-icon
        :message="`采集失败：${failedRows[0]?.parseNote || '无解析说明'}。业务申请不会使用失败版本。`"
      />
      <Alert
        v-else-if="!activeRow && unpublishedRows.length"
        class="mb-3"
        type="warning"
        show-icon
        message="截至本次查询未检索到该年度公告，不能猜测日期。"
      />
      <Alert
        v-else-if="!activeRow"
        class="mb-3"
        type="warning"
        show-icon
        message="该年度节假日日历尚未发布或配置，请联系人事。"
      />

      <div v-if="activeRow" class="mb-4 text-sm text-gray-600">
        <div>
          状态：
          <Tag color="green">{{ statusLabel(activeRow.status) }}</Tag>
          法定 {{ legalCount }} 天
        </div>
        <div>
          来源：{{ activeRow.source || '—' }}
          <a
            v-if="activeRow.sourceUrl"
            :href="activeRow.sourceUrl"
            target="_blank"
            rel="noreferrer"
          >
            打开官方公告
          </a>
        </div>
        <div>采集/导入时间：{{ activeRow.fetchedAt || '—' }}</div>
        <div v-if="activeRow.parseNote">说明：{{ activeRow.parseNote }}</div>
        <Select
          v-model:value="kindFilter"
          class="mt-2"
          style="width: 180px"
          :options="[
            { label: '突出法定节假日', value: 'LEGAL' },
            { label: '特殊日（含周末/调休）', value: 'SPECIAL' },
            { label: '全年含普通工作日', value: 'ALL' },
          ]"
        />
      </div>

      <Table
        v-if="activeRow"
        class="mb-6"
        :columns="dayColumns"
        :data-source="visibleDays"
        :pagination="false"
        row-key="date"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'date'">
            <span :class="record.kind === 'LEGAL_HOLIDAY' ? 'font-semibold text-red-600' : ''">
              {{ record.date }}
            </span>
          </template>
          <template v-else-if="column.dataIndex === 'kindLabel'">
            <Tag
              :color="
                record.kind === 'LEGAL_HOLIDAY'
                  ? 'red'
                  : record.kind === 'WEEKEND'
                    ? 'blue'
                    : record.kind === 'MAKEUP_WORKDAY'
                      ? 'orange'
                      : 'default'
              "
            >
              {{ record.kindLabel }}
            </Tag>
          </template>
          <template v-else-if="column.dataIndex === 'allowed'">
            {{ record.allowed ? '是' : '否' }}
          </template>
        </template>
      </Table>

      <p class="mb-3 text-sm text-gray-500">
        业务申请只读已核验 ACTIVE 版本。抓取结果进入待核验，不会自动覆盖。核验启用不改写历史加班单。
      </p>
      <Table
        :columns="versionColumns"
        :data-source="yearRows"
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
                    : record.status === 'FAILED'
                      ? 'red'
                      : record.status === 'SUPERSEDED'
                        ? 'blue'
                        : 'default'
              "
            >
              {{ statusLabel(record.status) }}
            </Tag>
            <Tag v-if="isSeedVersion(record)" class="ml-1">初始化已核验</Tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <Space v-if="canVerify && record.status === 'PENDING'">
              <Button type="link" @click="onVerify(record, true)">启用</Button>
              <Button type="link" danger @click="onVerify(record, false)">
                驳回
              </Button>
            </Space>
          </template>
        </template>
      </Table>

      <div v-for="pending in pendingRows" :key="pending.id" class="mt-4">
        <Card size="small" :title="`待核验 #${pending.id} 日期明细`">
          <pre class="mb-2 whitespace-pre-wrap text-xs">{{ pending.diffJson || '无差异 JSON' }}</pre>
          <Table
            :columns="dayColumns"
            :data-source="buildDayRows(pending, year)"
            :pagination="false"
            row-key="date"
            size="small"
          />
        </Card>
      </div>
    </Card>
  </Page>
</template>
