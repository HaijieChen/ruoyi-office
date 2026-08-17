<script lang="ts" setup>
import { nextTick, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import FormBody from './form-body.vue';

defineOptions({ name: 'FinancePaymentApplicationForm' });

const emit = defineEmits(['success']);

const bodyRef = ref<InstanceType<typeof FormBody>>();
const formTitle = ref('发起付款申请');

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) return;
    const data = modalApi.getData<{ id?: number; mode?: string }>() || {};
    formTitle.value =
      data.mode === 'resubmit' ? '重提付款申请' : '发起付款申请';
    await nextTick();
    if (!bodyRef.value) {
      await nextTick();
    }
    await bodyRef.value?.reset({ id: data.id, mode: data.mode });
  },
  async onConfirm() {
    modalApi.lock();
    try {
      await bodyRef.value?.submit();
      await modalApi.close();
    } catch {
      // validation / API error already messaged in body
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
  <Modal :title="formTitle" class="w-[720px]">
    <FormBody ref="bodyRef" @success="onSuccess" />
  </Modal>
</template>
