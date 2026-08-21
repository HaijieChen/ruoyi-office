<script lang="ts" setup>
import { onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Button, Form, FormItem, InputNumber, Radio, RadioGroup, Table, message } from 'ant-design-vue';

import { createMinWage, getMinWageHistory, type MinWageApi } from '#/api/hrm/payroll/min-wage';

defineOptions({ name: 'HrmMinWage' });

const amount = ref<number | null>(null);
const nextMonth = ref(false);
const rows = ref<MinWageApi.MinWageRow[]>([]);
const loading = ref(false);

async function loadHistory() {
  loading.value = true;
  try {
    rows.value = (await getMinWageHistory()) ?? [];
  } finally {
    loading.value = false;
  }
}

async function onSave() {
  if (amount.value == null) {
    message.warning('请输入最低工资');
    return;
  }
  await createMinWage({ amount: amount.value, nextMonth: nextMonth.value });
  message.success('已保存');
  amount.value = null;
  nextMonth.value = false;
  await loadHistory();
}

onMounted(loadHistory);
</script>

<template>
  <Page title="最低工资">
    <Form layout="inline" class="mb-4">
      <FormItem label="金额">
        <InputNumber v-model:value="amount" :min="0" :precision="2" />
      </FormItem>
      <FormItem label="生效">
        <RadioGroup v-model:value="nextMonth">
          <Radio :value="false">当月</Radio>
          <Radio :value="true">下月</Radio>
        </RadioGroup>
      </FormItem>
      <FormItem>
        <Button type="primary" @click="onSave">保存</Button>
      </FormItem>
    </Form>
    <Table
      :data-source="rows"
      :loading="loading"
      row-key="id"
      :columns="[
        { title: '生效年月', dataIndex: 'effectiveMonth' },
        { title: '金额', dataIndex: 'amount' },
        { title: '录入时间', dataIndex: 'createTime' },
      ]"
    />
  </Page>
</template>
