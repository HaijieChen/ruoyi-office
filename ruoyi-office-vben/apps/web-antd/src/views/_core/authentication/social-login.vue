<script lang="ts" setup>
import type { VbenFormSchema } from '@vben/common-ui';

import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { AuthenticationLogin, Verification, z } from '@vben/common-ui';
import { isCaptchaEnable } from '@vben/hooks';
import { $t } from '@vben/locales';
import { useAccessStore } from '@vben/stores';
import { getUrlValue } from '@vben/utils';

import { checkCaptcha, getCaptcha } from '#/api/core/auth';
import { bindFixedLoginTenant } from '#/constants/tenant';
import { useAuthStore } from '#/store';

defineOptions({ name: 'SocialLogin' });

const authStore = useAuthStore();
const accessStore = useAccessStore();
const { query } = useRoute();
const router = useRouter();
const captchaEnable = isCaptchaEnable();

const loginRef = ref();
const verifyRef = ref();

const captchaType = 'blockPuzzle'; // 验证码类型：'blockPuzzle' | 'clickWord'

/** 尝试登录：当账号已经绑定，socialLogin 会直接获得 token */
const socialType = Number(getUrlValue('type'));
const redirect = getUrlValue('redirect');
const socialCode = query?.code as string;
const socialState = query?.state as string;
async function tryLogin() {
  // 用于登录后，基于 redirect 的重定向
  if (redirect) {
    await router.replace({
      query: {
        ...query,
        redirect: encodeURIComponent(redirect),
      },
    });
  }

  // 尝试登录
  bindFixedLoginTenant(accessStore);
  await authStore.authLogin('social', {
    type: socialType,
    code: socialCode,
    state: socialState,
  });
}

/** 处理登录 */
async function handleLogin(values: any) {
  bindFixedLoginTenant(accessStore);
  // 如果开启验证码，则先验证验证码
  if (captchaEnable) {
    verifyRef.value.show();
    return;
  }

  // 无验证码，直接登录
  await authStore.authLogin('username', {
    ...values,
    socialType,
    socialCode,
    socialState,
  });
}

/** 验证码通过，执行登录 */
async function handleVerifySuccess({ captchaVerification }: any) {
  try {
    bindFixedLoginTenant(accessStore);
    await authStore.authLogin('username', {
      ...(await loginRef.value.getFormApi().getValues()),
      captchaVerification,
      socialType,
      socialCode,
      socialState,
    });
  } catch (error) {
    console.error('Error in handleLogin:', error);
  }
}

/** 组件挂载时固定登录租户 */
onMounted(async () => {
  bindFixedLoginTenant(accessStore);
  await tryLogin();
});

const formSchema = computed((): VbenFormSchema[] => {
  return [
    {
      component: 'VbenInput',
      componentProps: {
        placeholder: $t('authentication.usernameTip'),
      },
      fieldName: 'username',
      label: $t('authentication.username'),
      rules: z
        .string()
        .min(1, { message: $t('authentication.usernameTip') })
        .default(import.meta.env.VITE_APP_DEFAULT_USERNAME),
    },
    {
      component: 'VbenInputPassword',
      componentProps: {
        placeholder: $t('authentication.passwordTip'),
      },
      fieldName: 'password',
      label: $t('authentication.password'),
      rules: z
        .string()
        .min(1, { message: $t('authentication.passwordTip') })
        .default(import.meta.env.VITE_APP_DEFAULT_PASSWORD),
    },
  ];
});
</script>

<template>
  <div>
    <AuthenticationLogin
      ref="loginRef"
      :form-schema="formSchema"
      :loading="authStore.loginLoading"
      :show-code-login="false"
      :show-qrcode-login="false"
      :show-third-party-login="false"
      :show-register="false"
      @submit="handleLogin"
    />
    <Verification
      ref="verifyRef"
      v-if="captchaEnable"
      :captcha-type="captchaType"
      :check-captcha-api="checkCaptcha"
      :get-captcha-api="getCaptcha"
      :img-size="{ width: '400px', height: '200px' }"
      mode="pop"
      @on-success="handleVerifySuccess"
    />
  </div>
</template>
