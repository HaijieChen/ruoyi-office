<script lang="ts">
/**
 * 兼容旧菜单 component=dashboard/workspace/index 与书签 #/workspace。
 * 业务默认落地已改为 /home（可配真数据工作台）。
 *
 * 使用 beforeRouteEnter + hash 兜底：onMounted+useRouter.replace 在 keep-alive/
 * 动态路由场景下偶发不跳转（oa-test 手测复现）。
 */
import type { NavigationGuardNext, RouteLocationNormalized } from 'vue-router';

import { defineComponent, nextTick, onActivated, onMounted } from 'vue';
import { useRouter } from 'vue-router';

function hardRedirectHome() {
  const base = `${window.location.pathname}${window.location.search}`;
  if (window.location.hash !== '#/home') {
    window.location.replace(`${base}#/home`);
  }
}

export default defineComponent({
  name: 'Workspace',
  beforeRouteEnter(
    _to: RouteLocationNormalized,
    _from: RouteLocationNormalized,
    next: NavigationGuardNext,
  ) {
    next((vm) => {
      // 优先走 router；失败则硬跳 hash
      const router = (vm as any)?.$router;
      if (router?.replace) {
        router.replace({ path: '/home' }).catch(() => hardRedirectHome());
      } else {
        hardRedirectHome();
      }
    });
  },
  setup() {
    const router = useRouter();

    function goHome() {
      if (
        router.currentRoute.value.path === '/workspace' ||
        router.currentRoute.value.path.endsWith('/workspace')
      ) {
        router.replace({ path: '/home' }).catch(() => {
          hardRedirectHome();
        });
        // 短延迟兜底：replace 静默无效时强制 hash
        window.setTimeout(() => {
          if (
            window.location.hash.includes('workspace') ||
            router.currentRoute.value.name === 'Workspace'
          ) {
            hardRedirectHome();
          }
        }, 300);
      }
    }

    onMounted(() => {
      nextTick(goHome);
    });
    onActivated(() => {
      nextTick(goHome);
    });

    return {};
  },
});
</script>

<template>
  <div class="workspace-redirect" />
</template>
