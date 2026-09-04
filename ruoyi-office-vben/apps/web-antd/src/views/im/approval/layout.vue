<script lang="ts" setup>
import { computed } from 'vue';
import { RouterLink, RouterView, useRoute } from 'vue-router';

import { IM_APPROVAL_TABS } from './tabs';

const route = useRoute();
const activePath = computed(() => route.path);
</script>

<template>
  <div class="im-approval-shell">
    <main class="im-approval-shell__main">
      <RouterView />
    </main>
    <nav class="im-approval-shell__nav" aria-label="审批中心">
      <RouterLink
        v-for="tab in IM_APPROVAL_TABS"
        :key="tab.key"
        :to="tab.path"
        class="im-approval-shell__tab"
        :class="{ 'is-active': activePath === tab.path }"
      >
        {{ tab.label }}
      </RouterLink>
    </nav>
  </div>
</template>

<style scoped>
.im-approval-shell {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: #f5f5f5;
}
.im-approval-shell__main {
  flex: 1;
  overflow: auto;
  padding-bottom: 56px;
}
.im-approval-shell__nav {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  height: 56px;
  background: #fff;
  border-top: 1px solid #eee;
}
.im-approval-shell__tab {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #666;
  text-decoration: none;
}
.im-approval-shell__tab.is-active {
  color: #1677ff;
  font-weight: 600;
}
</style>
