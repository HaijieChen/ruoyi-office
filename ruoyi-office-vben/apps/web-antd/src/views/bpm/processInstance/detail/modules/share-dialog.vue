<script lang="ts" setup>
import { computed, ref, watch } from 'vue';

import { Button, Modal, Select, message } from 'ant-design-vue';

import {
  getProcessInstanceShareRecipients,
  revokeProcessInstanceShare,
  shareProcessInstance,
} from '#/api/bpm/processInstance';
import { getSimpleUserList } from '#/api/system/user';
import { useUserStore } from '@vben/stores';

const props = defineProps<{
  open: boolean;
  processInstanceId: string;
}>();

const emit = defineEmits<{
  'update:open': [boolean];
}>();

const userStore = useUserStore();
const loading = ref(false);
const userOptions = ref<{ label: string; value: number }[]>([]);
const selectedIds = ref<number[]>([]);
const recipients = ref<{ id: number; recipientUserId: number }[]>([]);

const otherUsers = computed(() =>
  userOptions.value.filter((u) => u.value !== userStore.userInfo?.id),
);

async function loadUsers() {
  const list = (await getSimpleUserList()) || [];
  userOptions.value = list
    .filter((u: any) => u.id != null)
    .map((u: any) => ({ label: u.nickname || u.username, value: u.id }));
}

async function loadRecipients() {
  if (!props.processInstanceId) return;
  try {
    loading.value = true;
    recipients.value =
      (await getProcessInstanceShareRecipients(props.processInstanceId)) || [];
  } catch {
    recipients.value = [];
  } finally {
    loading.value = false;
  }
}

watch(
  () => props.open,
  async (open) => {
    if (!open) return;
    selectedIds.value = [];
    await Promise.all([loadUsers(), loadRecipients()]);
  },
);

async function handleShare() {
  if (!selectedIds.value.length) {
    message.warning('请选择要分享的同事');
    return;
  }
  loading.value = true;
  try {
    await shareProcessInstance({
      processInstanceId: props.processInstanceId,
      recipientUserIds: selectedIds.value,
    });
    message.success('已分享');
    selectedIds.value = [];
    await loadRecipients();
  } finally {
    loading.value = false;
  }
}

async function handleRevoke(userId: number) {
  loading.value = true;
  try {
    await revokeProcessInstanceShare({
      processInstanceId: props.processInstanceId,
      recipientUserId: userId,
    });
    message.success('已收回');
    await loadRecipients();
  } finally {
    loading.value = false;
  }
}

function recipientLabel(id: number) {
  return otherUsers.value.find((u) => u.value === id)?.label || `用户#${id}`;
}
</script>

<template>
  <Modal
    :open="open"
    title="分享流程"
    :footer="null"
    destroy-on-close
    @cancel="emit('update:open', false)"
  >
    <div class="mb-3 flex gap-2">
      <Select
        v-model:value="selectedIds"
        class="flex-1"
        mode="multiple"
        show-search
        option-filter-prop="label"
        :options="otherUsers"
        placeholder="选择本租户同事"
        :loading="loading"
      />
      <Button type="primary" :loading="loading" @click="handleShare">
        分享
      </Button>
    </div>
    <div v-if="!recipients.length" class="text-gray-400">暂无已分享对象</div>
    <div
      v-for="row in recipients"
      :key="row.id"
      class="mb-2 flex items-center justify-between"
    >
      <span>{{ recipientLabel(row.recipientUserId) }}</span>
      <Button type="link" danger @click="handleRevoke(row.recipientUserId)">
        收回
      </Button>
    </div>
  </Modal>
</template>
