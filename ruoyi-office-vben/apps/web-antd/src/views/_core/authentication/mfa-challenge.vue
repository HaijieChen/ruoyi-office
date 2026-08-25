<script lang="ts" setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { LOGIN_PATH } from '@vben/constants';
import { useAccessStore } from '@vben/stores';

import { Button, Input, Select, message } from 'ant-design-vue';

import { mfaSendCodeApi, mfaVerifyApi } from '#/api';
import { bindFixedLoginTenant } from '#/constants/tenant';
import { useAuthStore } from '#/store';
import {
  clearMfaFlow,
  isMfaFlowExpired,
  loadMfaFlow,
} from '#/utils/mfa-flow';
import type { MfaStoredFlow } from '#/utils/mfa-flow';

defineOptions({ name: 'MfaChallenge' });

const router = useRouter();
const authStore = useAuthStore();
const accessStore = useAccessStore();

const flow = ref<MfaStoredFlow | null>(null);
const factorId = ref('');
const code = ref('');
const sending = ref(false);
const submitting = ref(false);
const now = ref(Date.now());
let timer: ReturnType<typeof setInterval> | undefined;

const factors = computed(() => flow.value?.factors ?? []);
const selected = computed(() =>
  factors.value.find((item) => item.id === factorId.value) ?? factors.value[0],
);
const selectedType = computed(() => (selected.value?.type || '').toUpperCase());
const canSend = computed(() => {
  const actions = flow.value?.allowedActions ?? [];
  return (
    actions.includes('send') &&
    (selectedType.value === 'SMS' || selectedType.value === 'EMAIL')
  );
});
const remainSeconds = computed(() => {
  if (!flow.value?.expiresIn) {
    return null;
  }
  const end = flow.value.savedAt + flow.value.expiresIn * 1000;
  return Math.max(0, Math.ceil((end - now.value) / 1000));
});

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

function factorLabel(item: { type?: string; label?: string; maskedTarget?: string }) {
  const name = factorTypeName(item.type);
  return item.maskedTarget ? name + ' (' + item.maskedTarget + ')' : name;
}

function backToLogin() {
  clearMfaFlow();
  router.replace(LOGIN_PATH);
}

async function sendCode() {
  if (!flow.value?.flowToken || !factorId.value) {
    return;
  }
  sending.value = true;
  try {
    await mfaSendCodeApi({
      flowToken: flow.value.flowToken,
      factorId: factorId.value,
    });
    message.success('验证码已发送');
  } finally {
    sending.value = false;
  }
}

async function submit() {
  if (!flow.value?.flowToken || !factorId.value || !code.value) {
    message.warning('请输入验证码');
    return;
  }
  submitting.value = true;
  try {
    const result = await mfaVerifyApi({
      flowToken: flow.value.flowToken,
      factorId: factorId.value,
      code: code.value.trim(),
    });
    await authStore.finishMfaLogin(result);
  } finally {
    submitting.value = false;
  }
}

onMounted(() => {
  bindFixedLoginTenant(accessStore);
  const current = loadMfaFlow();
  if (!current || isMfaFlowExpired(current)) {
    message.warning('验证流程已失效，请重新登录');
    backToLogin();
    return;
  }
  flow.value = current;
  factorId.value = current.factors?.[0]?.id || '';
  timer = setInterval(() => {
    now.value = Date.now();
    if (isMfaFlowExpired(flow.value)) {
      message.warning('验证已超时，请重新登录');
      backToLogin();
    }
  }, 1000);
});

onBeforeUnmount(() => {
  if (timer) {
    clearInterval(timer);
  }
});
</script>

<template>
  <div class="w-full space-y-5">
    <div>
      <h2 class="text-xl font-semibold">二次验证</h2>
      <p class="mt-1 text-sm text-muted-foreground">
        账号密码已通过，请完成第二步验证。
      </p>
    </div>

    <div v-if="remainSeconds !== null" class="text-sm text-muted-foreground">
      剩余 {{ remainSeconds }} 秒
    </div>

    <div v-if="factors.length > 1" class="space-y-2">
      <div class="text-sm">验证方式</div>
      <Select
        v-model:value="factorId"
        class="w-full"
        :options="factors.map((item) => ({ label: factorLabel(item), value: item.id }))"
      />
    </div>
    <div v-else-if="selected" class="text-sm text-muted-foreground">
      当前因子：{{ factorLabel(selected) }}
    </div>

    <Input
      v-model:value="code"
      :maxlength="6"
      placeholder="6 位数字验证码"
      size="large"
      @press-enter="submit"
    />

    <Button
      v-if="canSend"
      block
      :loading="sending"
      @click="sendCode"
    >
      发送验证码
    </Button>

    <Button type="primary" block size="large" :loading="submitting" @click="submit">
      验证并登录
    </Button>
    <Button block type="link" @click="backToLogin">返回登录</Button>
  </div>
</template>
