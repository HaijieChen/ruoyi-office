<script lang="ts" setup>
import { onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Button, Form, Input, InputNumber, Table, message } from 'ant-design-vue';
import dayjs from 'dayjs';

import {
  getExchangeRatePage,
  saveExchangeRate,
  type FinanceExchangeRateApi,
} from '#/api/finance/exchange-rate';

defineOptions({ name: 'FinanceExchangeRate' });

const loading = ref(false);
const list = ref<FinanceExchangeRateApi.Row[]>([]);
const form = ref<FinanceExchangeRateApi.Row>({
  periodLabel: dayjs().format('YYYY-MM'),
  fromCurrency: 'USD',
  toCurrency: 'CNY',
  rate: 1,
});

async function load() {
  loading.value = true;
  try {
    const page = await getExchangeRatePage({ pageNo: 1, pageSize: 50 });
    list.value = page?.list || [];
  } finally {
    loading.value = false;
  }
}

async function onSave() {
  await saveExchangeRate({ ...form.value });
  message.success('已保存');
  load();
}

onMounted(load);
</script>

<template>
  <Page auto-content-height>
    <div class="p-4">
      <Form layout="inline" class="mb-4">
        <Form.Item label="月份">
          <Input v-model:value="form.periodLabel" placeholder="YYYY-MM" class="w-28" />
        </Form.Item>
        <Form.Item label="从">
          <Input v-model:value="form.fromCurrency" class="w-20" />
        </Form.Item>
        <Form.Item label="到">
          <Input v-model:value="form.toCurrency" class="w-20" />
        </Form.Item>
        <Form.Item label="汇率">
          <InputNumber v-model:value="form.rate" :min="0" :precision="8" />
        </Form.Item>
        <Button type="primary" @click="onSave">保存本月汇率</Button>
      </Form>
      <Table
        row-key="id"
        size="small"
        :loading="loading"
        :data-source="list"
        :pagination="false"
        :columns="[
          { title: '月份', dataIndex: 'periodLabel' },
          { title: '从', dataIndex: 'fromCurrency' },
          { title: '到', dataIndex: 'toCurrency' },
          { title: '汇率', dataIndex: 'rate' },
        ]"
      />
    </div>
  </Page>
</template>
