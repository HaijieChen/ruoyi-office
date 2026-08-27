<script lang="ts" setup>
import { onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Alert, Button, Card, Checkbox, Form, Radio, Spin, message } from 'ant-design-vue';

import {
  getMfaPolicy,
  updateMfaPolicy,
} from '#/api/system/mfa/policy';

defineOptions({ name: 'SystemMfaPolicy' });

const loading = ref(false);
const saving = ref(false);
const mode = ref<'OFF' | 'OPTIONAL' | 'REQUIRED'>('OFF');
const allowedFactors = ref<string[]>([]);

const factorOptions = [
  { label: 'TOTP（验证器）', value: 'TOTP' },
  { label: '短信', value: 'SMS' },
  { label: '邮箱', value: 'EMAIL' },
];

async function load() {
  loading.value = true;
  try {
    const data = await getMfaPolicy();
    const next = (data?.mode || 'OFF').toUpperCase();
    mode.value =
      next === 'OPTIONAL' || next === 'REQUIRED' ? next : 'OFF';
    allowedFactors.value = [...(data?.allowedFactors || [])];
  } finally {
    loading.value = false;
  }
}

async function handleSave() {
  if (mode.value !== 'OFF' && allowedFactors.value.length === 0) {
    message.error('可选或强制时请至少选择一种认证因子');
    return;
  }
  saving.value = true;
  try {
    await updateMfaPolicy({
      mode: mode.value,
      allowedFactors: mode.value === 'OFF' ? [] : allowedFactors.value,
    });
    message.success('已保存');
    await load();
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>

<template>
  <Page auto-content-height>
    <Card title="MFA 策略" class="max-w-2xl">
      <Alert
        class="mb-4"
        type="info"
        show-icon
        message="超级管理员（super_admin）登录始终需要 MFA，不受此开关影响。"
      />
      <Spin :spinning="loading">
        <Form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
          <Form.Item label="登录 MFA">
            <Radio.Group v-model:value="mode">
              <Radio.Button value="OFF">关</Radio.Button>
              <Radio.Button value="OPTIONAL">可选</Radio.Button>
              <Radio.Button value="REQUIRED">强制</Radio.Button>
            </Radio.Group>
          </Form.Item>
          <Form.Item v-if="mode !== 'OFF'" label="允许因子">
            <Checkbox.Group
              v-model:value="allowedFactors"
              :options="factorOptions"
            />
          </Form.Item>
          <Form.Item :wrapper-col="{ offset: 6, span: 16 }">
            <Button
              type="primary"
              :loading="saving"
              v-access:code="['system:mfa-policy:update']"
              @click="handleSave"
            >
              保存
            </Button>
          </Form.Item>
        </Form>
      </Spin>
    </Card>
  </Page>
</template>
