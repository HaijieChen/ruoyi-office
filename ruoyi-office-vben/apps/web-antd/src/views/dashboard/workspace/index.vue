<script lang="ts" setup>
/**
 * 兼容旧菜单 component=dashboard/workspace/index 与书签 #/workspace。
 * 业务默认落地已改为 /home（可配真数据工作台），此处仅做重定向。
 */
import { onMounted } from 'vue';
import { useRouter } from 'vue-router';

defineOptions({ name: 'Workspace' });

const router = useRouter();

onMounted(() => {
  router.replace('/home').catch((error) => {
    console.error('Redirect /workspace → /home failed:', error);
  });
});
</script>

<template>
  <div class="workspace-redirect" />
</template>
