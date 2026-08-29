<script lang="ts" setup>
import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Select } from 'ant-design-vue';

import { getSimpleRoleList, type SystemRoleApi } from '#/api/system/role';

defineOptions({ name: 'HrmGenerateUserRoleModal' });

const roleOptions = ref<SystemRoleApi.Role[]>([]);
const roleIds = ref<number[]>([]);

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const data = modalApi.getData<{
      resolve?: (ids: number[] | null) => void;
      settled?: boolean;
    }>();
    if (data) {
      data.settled = true;
      data.resolve?.(roleIds.value);
    }
    await modalApi.close();
  },
  onClosed() {
    const data = modalApi.getData<{
      resolve?: (ids: number[] | null) => void;
      settled?: boolean;
    }>();
    if (data && !data.settled) {
      data.settled = true;
      data.resolve?.(null);
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      return;
    }
    roleIds.value = [];
    const data = modalApi.getData<{ settled?: boolean }>();
    if (data) {
      data.settled = false;
    }
    if (roleOptions.value.length === 0) {
      roleOptions.value = (await getSimpleRoleList()) ?? [];
    }
  },
});
</script>

<template>
  <Modal title="选择角色（可不选）">
    <div class="px-4 py-2">
      <Select
        v-model:value="roleIds"
        allow-clear
        class="w-full"
        mode="multiple"
        option-filter-prop="label"
        placeholder="请选择角色，可不选"
        :options="roleOptions.map((r) => ({ label: r.name, value: r.id }))"
      />
    </div>
  </Modal>
</template>
