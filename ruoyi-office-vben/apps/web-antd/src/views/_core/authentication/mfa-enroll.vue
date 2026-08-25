<script lang="ts" setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { LOGIN_PATH } from '@vben/constants';
import { useAccessStore } from '@vben/stores';

import { Button, Input, QRCode, message } from 'ant-design-vue';

import {
  mfaEnrollmentTotpConfirmApi,
  mfaEnrollmentTotpStartApi,
} from '#/api';
import { bindFixedLoginTenant } from '#/constants/tenant';
import { useAuthStore } from '#/store';
import {
  clearMfaFlow,
  isMfaFlowExpired,
  loadMfaFlow,
  saveMfaFlow,
} from '#/utils/mfa-flow';

defineOptions({ name: 'MfaEnroll' });

const router = useRouter();
const authStore = useAuthStore();
const accessStore = useAccessStore();

const loading = ref(true);
const submitting = ref(false);
const flowToken = ref('');
const factorId = ref('');
const secretManual = ref('');
const otpauthUri = ref('');
const code = ref('');

function backToLogin() {
  clearMfaFlow();
  router.replace(LOGIN_PATH);
}

async function startEnroll() {
  const current = loadMfaFlow();
  if (!current || isMfaFlowExpired(current) || !current.flowToken) {
    message.warning('绑定流程已失效，请重新登录');
    backToLogin();
    return;
  }
  loading.value = true;
  try {
    const started = await mfaEnrollmentTotpStartApi({
      flowToken: current.flowToken,
    });
    flowToken.value = started.flowToken || current.flowToken;
    factorId.value = started.factorId || '';
    secretManual.value = started.secretManual || '';
    otpauthUri.value = started.otpauthUri || '';
    if (started.flowToken) {
      saveMfaFlow({ ...current, flowToken: started.flowToken });
    }
  } catch {
    backToLogin();
  } finally {
    loading.value = false;
  }
}

async function confirm() {
  if (!flowToken.value || !factorId.value || !code.value) {
    message.warning('请输入验证器中的 6 位验证码');
    return;
  }
  submitting.value = true;
  try {
    const result = await mfaEnrollmentTotpConfirmApi({
      flowToken: flowToken.value,
      factorId: factorId.value,
      code: code.value.trim(),
    });
    await authStore.finishMfaLogin(result);
  } finally {
    submitting.value = false;
  }
}

async function copySecret() {
  if (!secretManual.value) {
    return;
  }
  try {
    await navigator.clipboard.writeText(secretManual.value);
    message.success('密钥已复制');
  } catch {
    message.warning('复制失败，请手动抄写密钥');
  }
}

onMounted(() => {
  bindFixedLoginTenant(accessStore);
  void startEnroll();
});
</script>

<template>
  <div class="w-full space-y-5">
    <div>
      <h2 class="text-xl font-semibold">绑定验证器</h2>
      <p class="mt-1 text-sm text-muted-foreground">
        使用 Google Authenticator / 1Password 等应用扫描二维码，或手工录入密钥。
      </p>
    </div>

    <div v-if="loading" class="text-sm text-muted-foreground">正在生成绑定信息…</div>

    <template v-else>
      <div v-if="otpauthUri" class="flex justify-center rounded bg-white p-3">
        <QRCode :value="otpauthUri" :size="200" />
      </div>
      <div v-if="secretManual" class="space-y-2 rounded border p-3 text-sm">
        <div class="text-muted-foreground">手工密钥（仅显示一次）</div>
        <div class="break-all font-mono">{{ secretManual }}</div>
        <Button size="small" @click="copySecret">复制密钥</Button>
      </div>

      <Input
        v-model:value="code"
        :maxlength="8"
        placeholder="请输入验证器中的 6 位验证码"
        size="large"
        @press-enter="confirm"
      />
      <Button type="primary" block size="large" :loading="submitting" @click="confirm">
        确认绑定并登录
      </Button>
    </template>

    <Button block type="link" @click="backToLogin">返回登录</Button>
  </div>
</template>
