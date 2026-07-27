<script lang="ts" setup>
import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Form, FormItem, Input, message } from 'ant-design-vue';

import { closeReceipt, reopenReceipt } from '#/api/finance/receipt';

defineOptions({ name: 'FinanceReceiptLifecycleModal' });

const emit = defineEmits(['success']);

const action = ref<'close' | 'reopen'>('close');
const receiptId = ref<number>(0);
const reason = ref('');
const submitting = ref(false);

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    if (!reason.value.trim()) {
      message.warning('请填写操作原因');
      return;
    }
    submitting.value = true;
    modalApi.lock();
    try {
      if (action.value === 'close') {
        await closeReceipt(receiptId.value, reason.value.trim());
        message.success('回单已关闭');
      } else {
        await reopenReceipt(receiptId.value, reason.value.trim());
        message.success('回单已重开');
      }
      await modalApi.close();
      emit('success');
    } finally {
      submitting.value = false;
      modalApi.unlock();
    }
  },
  onClosed() {
    reason.value = '';
  },
});

function open(params: { action: 'close' | 'reopen'; receiptId: number }) {
  action.value = params.action;
  receiptId.value = params.receiptId;
  reason.value = '';
  modalApi.open();
}

defineExpose({ open });
</script>

<template>
  <Modal :title="action === 'close' ? '关闭回单' : '重开回单'" class="w-2/5">
    <div class="mx-4">
      <Form layout="vertical">
        <FormItem label="操作原因" required>
          <Input
            v-model:value="reason"
            :placeholder="action === 'close' ? '请填写关闭原因' : '请填写重开原因'"
            allow-clear
          />
        </FormItem>
      </Form>
    </div>
  </Modal>
</template>
