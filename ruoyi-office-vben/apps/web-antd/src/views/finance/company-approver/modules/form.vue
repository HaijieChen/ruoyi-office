<script lang="ts" setup>
import type { FinanceCompanyApproverApi } from '#/api/finance/company-approver';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Form, Select, message } from 'ant-design-vue';

import { saveCompanyApprover } from '#/api/finance/company-approver';
import { getSimpleUserList } from '#/api/system/user';

defineOptions({ name: 'FinanceCompanyApproverForm' });

const emit = defineEmits(['success']);

const formRef = ref();
const companyName = ref('');
const formData = ref<{ entityCompanyDeptId?: number; userIds: number[] }>({
  userIds: [],
});
const userOptions = ref<{ label: string; value: number }[]>([]);

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) return;
    const data = modalApi.getData<FinanceCompanyApproverApi.Item>() || {};
    companyName.value = data.entityCompanyName || '';
    formData.value = {
      entityCompanyDeptId: data.entityCompanyDeptId,
      userIds: [...(data.userIds || [])],
    };
    if (!userOptions.value.length) {
      const list = (await getSimpleUserList()) || [];
      userOptions.value = list
        .filter((u) => u.id != null)
        .map((u) => ({
          value: u.id as number,
          label: `${u.nickname || u.username} (${u.username})`,
        }));
    }
  },
  async onConfirm() {
    if (!formData.value.entityCompanyDeptId) return;
    if (!formData.value.userIds.length) {
      message.error('请至少选择一名财务审批人');
      return;
    }
    modalApi.lock();
    try {
      await saveCompanyApprover({
        entityCompanyDeptId: formData.value.entityCompanyDeptId,
        userIds: formData.value.userIds,
      });
      message.success('已保存');
      emit('success');
      await modalApi.close();
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal :title="`分配财务审批人 · ${companyName}`" class="w-[520px]">
    <Form ref="formRef" :model="formData" layout="vertical">
      <Form.Item label="财务审批人" required>
        <Select
          v-model:value="formData.userIds"
          mode="multiple"
          :options="userOptions"
          :filter-option="
            (input: string, option: any) =>
              String(option?.label || '')
                .toLowerCase()
                .includes(input.toLowerCase())
          "
          placeholder="可多选，流程按或签"
        />
      </Form.Item>
    </Form>
  </Modal>
</template>
