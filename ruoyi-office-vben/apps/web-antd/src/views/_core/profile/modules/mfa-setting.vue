<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue';

import { Button, Input, QRCode, message } from 'ant-design-vue';

import {
  confirmMyEmail,
  confirmMyTotp,
  deleteMyMfaFactor,
  getMyMfaFactors,
  startMyEmail,
  startMyTotp,
} from '#/api/system/mfa/factor';
import type { SystemMfaFactorApi } from '#/api/system/mfa/factor';

const loading = ref(false);
const binding = ref(false);
const confirming = ref(false);
const factors = ref<SystemMfaFactorApi.Factor[]>([]);
const pendingKind = ref<'totp' | 'email' | ''>('');
const pendingId = ref('');
const otpauthUri = ref('');
const secretManual = ref('');
const maskedEmail = ref('');
const code = ref('');

function factorTypeName(type?: string) {
  const t = (type || '').toUpperCase();
  if (t === 'TOTP') {
    return '身份验证器';
  }
  if (t === 'EMAIL') {
    return '邮箱';
  }
  if (t === 'SMS') {
    return '短信';
  }
  return type || '验证方式';
}

const hasEmail = computed(() =>
  factors.value.some((item) => (item.type || '').toUpperCase() === 'EMAIL'),
);

async function load() {
  loading.value = true;
  try {
    factors.value = (await getMyMfaFactors()) || [];
  } finally {
    loading.value = false;
  }
}

function resetPending() {
  pendingKind.value = '';
  pendingId.value = '';
  otpauthUri.value = '';
  secretManual.value = '';
  maskedEmail.value = '';
  code.value = '';
}

async function startBindTotp() {
  binding.value = true;
  try {
    const started = await startMyTotp();
    pendingKind.value = 'totp';
    pendingId.value = started.factorId || '';
    otpauthUri.value = started.otpauthUri || '';
    secretManual.value = started.secretManual || '';
    code.value = '';
  } finally {
    binding.value = false;
  }
}

async function startBindEmail() {
  binding.value = true;
  try {
    const started = await startMyEmail();
    pendingKind.value = 'email';
    pendingId.value = started.factorId || '';
    maskedEmail.value = started.maskedEmail || '';
    otpauthUri.value = '';
    secretManual.value = '';
    code.value = '';
    message.success('验证码已发送到 ' + (started.maskedEmail || '邮箱'));
  } finally {
    binding.value = false;
  }
}

async function confirm() {
  if (!pendingId.value || !code.value) {
    message.warning('请输入验证码');
    return;
  }
  confirming.value = true;
  try {
    if (pendingKind.value === 'email') {
      await confirmMyEmail({ factorId: pendingId.value, code: code.value.trim() });
    } else {
      await confirmMyTotp({ factorId: pendingId.value, code: code.value.trim() });
    }
    message.success('MFA 设备已绑定');
    resetPending();
    await load();
  } finally {
    confirming.value = false;
  }
}

async function unbind(factorId: string) {
  await deleteMyMfaFactor(factorId);
  message.success('已解绑');
  await load();
}

async function copySecret() {
  if (!secretManual.value) {
    return;
  }
  try {
    await navigator.clipboard.writeText(secretManual.value);
    message.success('密钥已复制');
  } catch {
    message.warning('复制失败，请手动抄写');
  }
}

onMounted(() => {
  void load();
});
</script>

<template>
  <div class="space-y-5">
    <div>
      <div class="text-base font-medium">MFA 设备</div>
      <div class="mt-1 text-sm text-muted-foreground">
        可绑定验证器或邮箱。登录时按已绑因子做二次确认。
      </div>
    </div>

    <div v-if="loading" class="text-sm text-muted-foreground">加载中…</div>

    <div v-else-if="factors.length" class="space-y-3">
      <div
        v-for="item in factors"
        :key="item.id"
        class="flex items-center justify-between rounded border px-3 py-2"
      >
        <div>
          <div class="font-medium">{{ factorTypeName(item.type) }}</div>
          <div class="text-xs text-muted-foreground">
            {{ item.type }} · {{ item.maskedTarget || item.id }}
          </div>
        </div>
        <Button danger size="small" @click="unbind(item.id)">解绑</Button>
      </div>
    </div>
    <div v-else class="text-sm text-muted-foreground">尚未绑定 MFA 设备</div>

    <template v-if="pendingKind === 'totp'">
      <div v-if="otpauthUri" class="flex justify-center rounded bg-white p-3">
        <QRCode :value="otpauthUri" :size="180" />
      </div>
      <div v-if="secretManual" class="space-y-2 rounded border p-3 text-sm">
        <div class="text-muted-foreground">手工密钥（仅显示一次）</div>
        <div class="break-all font-mono">{{ secretManual }}</div>
        <Button size="small" @click="copySecret">复制密钥</Button>
      </div>
      <Input v-model:value="code" :maxlength="8" placeholder="请输入验证器中的 6 位验证码" @press-enter="confirm" />
      <div class="flex gap-2">
        <Button type="primary" :loading="confirming" @click="confirm">确认绑定</Button>
        <Button @click="resetPending">取消</Button>
      </div>
    </template>

    <template v-else-if="pendingKind === 'email'">
      <div class="text-sm text-muted-foreground">验证码已发送到 {{ maskedEmail }}</div>
      <Input v-model:value="code" :maxlength="6" placeholder="6 位数字验证码" @press-enter="confirm" />
      <div class="flex gap-2">
        <Button type="primary" :loading="confirming" @click="confirm">确认绑定</Button>
        <Button @click="resetPending">取消</Button>
      </div>
    </template>

    <div v-else class="flex flex-wrap gap-2">
      <Button type="primary" :loading="binding" @click="startBindTotp">绑定验证器</Button>
      <Button v-if="!hasEmail" :loading="binding" @click="startBindEmail">绑定邮箱</Button>
    </div>
  </div>
</template>
