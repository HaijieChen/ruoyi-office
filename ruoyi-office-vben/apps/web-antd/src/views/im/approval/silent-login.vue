<script lang="ts" setup>
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { imSilentLogin } from '#/api/core/auth';
import { useAuthStore } from '#/store';

import { resolveImSilentRedirect } from './tabs';

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const message = ref('正在进入审批中心…');

onMounted(async () => {
  const type = Number(route.query.type);
  const code = String(route.query.code ?? '');
  const state = String(route.query.state ?? 'im');
  if (!type || !code) {
    message.value = '请先在电脑个人中心绑定企微 / 钉钉 / 飞书，再从工作台进入。';
    return;
  }
  try {
    const loginResult = await imSilentLogin({ type, code, state });
    await authStore.completeAuthenticatedLogin(loginResult, async () => {
      const next = resolveImSilentRedirect({
        id: route.query.id as string | undefined,
        taskId: route.query.taskId as string | undefined,
      });
      await router.replace(next);
    });
  } catch {
    message.value =
      '无法免登。未绑定请先在电脑个人中心绑定；超级管理员请使用电脑登录。';
  }
});
</script>

<template>
  <div class="im-silent-login">{{ message }}</div>
</template>

<style scoped>
.im-silent-login {
  padding: 48px 16px;
  font-size: 14px;
  line-height: 1.6;
  color: #333;
  text-align: center;
}
</style>
