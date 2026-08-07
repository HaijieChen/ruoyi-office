<script lang="ts" setup>
import { nextTick, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import FormBody from './form-body.vue';

defineOptions({ name: 'FinanceInvoiceApplicationForm' });

const emit = defineEmits(['success']);

const bodyRef = ref<InstanceType<typeof FormBody>>();
const formTitle = ref('提交开票申请（无草稿）');

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) return;
    const data = modalApi.getData<{ id?: number; mode?: string }>() || {};
    formTitle.value =
      data.mode === 'resubmit'
        ? '驳回后重提开票申请'
        : '提交开票申请（无草稿）';
    await nextTick();
    await bodyRef.value?.reset({ id: data.id, mode: data.mode });
  },
  async onConfirm() {
    modalApi.lock();
    try {
      await bodyRef.value?.submit();
      await modalApi.close();
    } catch {
      // body already messaged
    } finally {
      modalApi.unlock();
    }
  },
});

function onSuccess() {
  emit('success');
}
</script>

<template>
  <Modal :title="formTitle" class="w-[800px]">
    <FormBody ref="bodyRef" @success="onSuccess" />
  </Modal>
</template>
