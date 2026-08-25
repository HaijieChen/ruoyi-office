import type { AuthPermissionInfo, Recordable, UserInfo } from '@vben/types';

import type { AuthApi } from '#/api';

import { ref } from 'vue';
import { useRouter } from 'vue-router';

import { LOGIN_PATH } from '@vben/constants';
import { preferences } from '@vben/preferences';
import { resetAllStores, useAccessStore, useUserStore } from '@vben/stores';

import { notification } from 'ant-design-vue';
import { defineStore } from 'pinia';

import {
  getAuthPermissionInfoApi,
  loginApi,
  logoutApi,
  register,
  smsLogin,
  socialLogin,
} from '#/api';
import { FIXED_LOGIN_TENANT_ID } from '#/constants/tenant';
import { $t } from '#/locales';
import {
  clearMfaFlow,
  resolveLoginNext,
  saveMfaFlow,
} from '#/utils/mfa-flow';

export const useAuthStore = defineStore('auth', () => {
  const accessStore = useAccessStore();
  const userStore = useUserStore();
  const router = useRouter();

  const loginLoading = ref(false);

  /**
   * 异步处理登录操作
   * Asynchronously handle the login process
   * @param type 登录类型
   * @param params 登录表单数据
   * @param onSuccess 登录成功后的回调函数
   */
  async function authLogin(
    type: 'mobile' | 'register' | 'social' | 'username',
    params: Recordable<any>,
    onSuccess?: () => Promise<void> | void,
  ) {
    // 异步处理用户登录操作并获取 accessToken
    let userInfo: null | UserInfo = null;
    try {
      let loginResult: AuthApi.LoginResult;
      loginLoading.value = true;
      switch (type) {
        case 'mobile': {
          loginResult = await smsLogin(params as AuthApi.SmsLoginParams);
          break;
        }
        case 'register': {
          loginResult = await register(params as AuthApi.RegisterParams);
          break;
        }
        case 'social': {
          loginResult = await socialLogin(params as AuthApi.SocialLoginParams);
          break;
        }
        default: {
          loginResult = await loginApi(params);
        }
      }
      userInfo = await finishMfaLogin(loginResult, onSuccess);
    } finally {
      loginLoading.value = false;
    }

    return {
      userInfo,
    };
  }

  async function completeAuthenticatedLogin(
    loginResult: AuthApi.LoginResult,
    onSuccess?: () => Promise<void> | void,
  ) {
    const { accessToken, refreshToken } = loginResult;
    if (!accessToken) {
      return null;
    }
    clearMfaFlow();
    accessStore.setAccessToken(accessToken);
    accessStore.setRefreshToken(refreshToken);

    const fetchUserInfoResult = await fetchUserInfo();
    const userInfo = fetchUserInfoResult.user;

    if (accessStore.loginExpired) {
      accessStore.setLoginExpired(false);
    } else {
      onSuccess
        ? await onSuccess?.()
        : await router.push(
            userInfo.homePath || preferences.app.defaultHomePath,
          );
    }

    if (userInfo?.nickname) {
      notification.success({
        description: `${$t('authentication.loginSuccessDesc')}:${userInfo?.nickname}`,
        duration: 3,
        message: $t('authentication.loginSuccess'),
      });
    }
    return userInfo;
  }

  async function finishMfaLogin(
    loginResult: AuthApi.LoginResult,
    onSuccess?: () => Promise<void> | void,
  ) {
    const next = resolveLoginNext(loginResult);
    if (next === 'home') {
      return completeAuthenticatedLogin(loginResult, onSuccess);
    }
    if (next === 'challenge' && loginResult.flow) {
      saveMfaFlow(loginResult.flow);
      await router.push({ name: 'MfaChallenge' });
      return null;
    }
    if (next === 'enroll' && loginResult.flow) {
      saveMfaFlow(loginResult.flow);
      await router.push({ name: 'MfaEnroll' });
      return null;
    }
    notification.warning({
      duration: 4,
      message: '当前登录需要额外验证，但未返回可用流程',
    });
    return null;
  }

  async function logout(redirect: boolean = true) {
    try {
      const accessToken = accessStore.accessToken as string;
      if (accessToken) {
        await logoutApi(accessToken);
      }
    } catch {
      // 不做任何处理
    }
    resetAllStores();
    clearMfaFlow();
    accessStore.setLoginExpired(false);
    // OA 固定登录租户，退出后仍保持 tenant-id=1，避免回登录页请求丢租户
    accessStore.setTenantId(FIXED_LOGIN_TENANT_ID);

    // 回登录页带上当前路由地址
    await router.replace({
      path: LOGIN_PATH,
      query: redirect
        ? {
            redirect: encodeURIComponent(router.currentRoute.value.fullPath),
          }
        : {},
    });
  }

  async function fetchUserInfo() {
    // 加载
    let authPermissionInfo: AuthPermissionInfo | null = null;
    authPermissionInfo = await getAuthPermissionInfoApi();
    // userStore
    userStore.setUserInfo(authPermissionInfo.user);
    userStore.setUserRoles(authPermissionInfo.roles);
    // accessStore
    accessStore.setAccessMenus(authPermissionInfo.menus);
    accessStore.setAccessCodes(authPermissionInfo.permissions);
    return authPermissionInfo;
  }

  function $reset() {
    loginLoading.value = false;
  }

  return {
    $reset,
    authLogin,
    completeAuthenticatedLogin,
    fetchUserInfo,
    finishMfaLogin,
    loginLoading,
    logout,
  };
});
