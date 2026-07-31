<script lang="ts" setup>
import type { WorkbenchQuickNavItem } from '@vben/common-ui';

import { computed } from 'vue';
import { useRouter } from 'vue-router';

import { WorkbenchQuickNav } from '@vben/common-ui';
import { openWindow } from '@vben/utils';

import { Empty } from 'ant-design-vue';

interface QuickNavItemInput {
  color?: string;
  icon?: string;
  title: string;
  url: string;
}

interface Props {
  /** 布局 config 注入；禁止回落商城/AI/ERP demo */
  items?: QuickNavItemInput[];
  title?: string;
}

const props = withDefaults(defineProps<Props>(), {
  items: () => [],
  title: '快捷入口',
});

const router = useRouter();

const quickNavItems = computed<WorkbenchQuickNavItem[]>(() => {
  const raw = props.items;
  if (!Array.isArray(raw) || raw.length === 0) {
    return [];
  }
  return raw
    .filter((item) => item && typeof item.title === 'string' && item.url)
    .map((item) => ({
      color: item.color || 'hsl(var(--primary))',
      icon: item.icon || 'lucide:link',
      title: item.title,
      url: item.url,
    }));
});

const isEmpty = computed(() => quickNavItems.value.length === 0);

function navTo(nav: WorkbenchQuickNavItem) {
  if (nav.url?.startsWith('http')) {
    openWindow(nav.url);
    return;
  }
  if (nav.url?.startsWith('/')) {
    router.push(nav.url).catch((error) => {
      console.error('Navigation failed:', error);
    });
  }
}
</script>

<template>
  <div class="workbench-quick-nav h-full">
    <WorkbenchQuickNav
      v-if="!isEmpty"
      :items="quickNavItems"
      :title="title"
      @click="navTo"
    />
    <div
      v-else
      class="flex h-full min-h-[120px] flex-col items-center justify-center rounded-lg bg-background p-4"
    >
      <Empty description="暂无快捷入口" :image="Empty.PRESENTED_IMAGE_SIMPLE" />
    </div>
  </div>
</template>
