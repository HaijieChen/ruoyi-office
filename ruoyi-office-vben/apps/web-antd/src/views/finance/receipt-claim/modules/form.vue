<script lang="ts" setup>
import type { Rule } from 'ant-design-vue/es/form';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import {
  Button,
  Form,
  InputNumber,
  Select,
  Space,
  Textarea,
  message,
} from 'ant-design-vue';

import {
  createClaim,
  getSourceBusinessOrderPage,
  getSourceReceiptPage,
  updateClaim,
} from '#/api/finance/receipt-claim';

defineOptions({ name: 'FinanceReceiptClaimForm' });

const emit = defineEmits(['success']);

interface LineItem {
  receiptId?: number;
  businessOrderId?: number;
  claimAmount?: number;
}

interface FormData {
  id?: number;
  remark?: string;
  items: LineItem[];
}

const formRef = ref();
const formData = ref<FormData>({ items: [{}] });
const receiptOptions = ref<
  Array<{ label: string; value: number; unclaimed?: number }>
>([]);
const orderOptions = ref<
  Array<{ label: string; value: number; remaining?: number }>
>([]);
const loadingOptions = ref(false);

const isEdit = computed(() => !!formData.value.id);
const getTitle = computed(() =>
  isEdit.value ? '修改到款认领' : '新建到款认领',
);

const rules: Record<string, Rule[]> = {
  items: [
    {
      validator: async () => {
        const items = formData.value.items || [];
        if (!items.length) {
          return Promise.reject('请至少添加一条认领明细');
        }
        for (const [idx, item] of items.entries()) {
          if (!item.receiptId) {
            return Promise.reject(`第 ${idx + 1} 行：请选择银行到款`);
          }
          if (!item.businessOrderId) {
            return Promise.reject(`第 ${idx + 1} 行：请选择商务单`);
          }
          if (!item.claimAmount || item.claimAmount <= 0) {
            return Promise.reject(`第 ${idx + 1} 行：认领金额须大于 0`);
          }
        }
        return Promise.resolve();
      },
      trigger: 'change',
    },
  ],
};

async function loadSourceOptions() {
  loadingOptions.value = true;
  try {
    const [receiptPage, orderPage] = await Promise.all([
      getSourceReceiptPage({ pageNo: 1, pageSize: 100 }),
      getSourceBusinessOrderPage({ pageNo: 1, pageSize: 100 }),
    ]);
    receiptOptions.value = (receiptPage?.list || []).map((r: any) => ({
      value: r.id,
      unclaimed: Number(r.unclaimedAmount ?? 0),
      label: `${r.receiptNo || r.id} | ${r.payerName || '-'} | 可认领 ¥${Number(r.unclaimedAmount ?? 0).toFixed(2)}`,
    }));
    orderOptions.value = (orderPage?.list || []).map((o: any) => ({
      value: o.id,
      remaining: Number(
        o.remainingBalance ??
          Number(o.settlementAmount ?? 0) - Number(o.confirmedClaimedAmount ?? 0),
      ),
      label: `${o.orderNo || o.id} | ${o.productName || '-'} | 可认领 ¥${Number(
        o.remainingBalance ??
          Number(o.settlementAmount ?? 0) - Number(o.confirmedClaimedAmount ?? 0),
      ).toFixed(2)}`,
    }));
  } finally {
    loadingOptions.value = false;
  }
}

function addLine() {
  formData.value.items.push({});
}

function removeLine(index: number) {
  if (formData.value.items.length <= 1) {
    message.warning('至少保留一条明细');
    return;
  }
  formData.value.items.splice(index, 1);
}

function resetForm() {
  formData.value = { items: [{}] };
  formRef.value?.resetFields();
}

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      return;
    }
    const data = modalApi.getData<FormData>() || {};
    formData.value = {
      id: data.id,
      remark: data.remark,
      items:
        data.items && data.items.length
          ? data.items.map((i) => ({
              receiptId: i.receiptId,
              businessOrderId: i.businessOrderId,
              claimAmount: i.claimAmount,
            }))
          : [{}],
    };
    await loadSourceOptions();
  },
  async onConfirm() {
    await formRef.value?.validate();
    modalApi.lock();
    try {
      const payload = {
        id: formData.value.id,
        remark: formData.value.remark,
        items: formData.value.items.map((i) => ({
          receiptId: i.receiptId as number,
          businessOrderId: i.businessOrderId as number,
          claimAmount: i.claimAmount as number,
        })),
      };
      if (payload.id) {
        await updateClaim(payload);
        message.success('认领单已更新');
      } else {
        await createClaim(payload);
        message.success('认领单已创建');
      }
      emit('success');
      modalApi.close();
    } finally {
      modalApi.unlock();
    }
  },
  onClosed() {
    resetForm();
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-[720px]">
    <Form
      ref="formRef"
      :model="formData"
      :rules="rules"
      :label-col="{ span: 4 }"
      :wrapper-col="{ span: 20 }"
      class="px-2"
    >
      <Form.Item label="说明" name="remark">
        <Textarea
          v-model:value="formData.remark"
          :rows="2"
          placeholder="可选：认领说明"
        />
      </Form.Item>

      <Form.Item label="认领明细" name="items" required>
        <div class="space-y-3">
          <div
            v-for="(item, index) in formData.items"
            :key="index"
            class="rounded border border-gray-200 p-3"
          >
            <div class="mb-2 flex items-center justify-between">
              <span class="text-sm font-medium">明细 {{ index + 1 }}</span>
              <Button type="link" danger size="small" @click="removeLine(index)">
                删除
              </Button>
            </div>
            <div class="grid grid-cols-1 gap-2">
              <div>
                <div class="mb-1 text-xs text-gray-500">银行到款</div>
                <Select
                  v-model:value="item.receiptId"
                  show-search
                  allow-clear
                  :loading="loadingOptions"
                  :options="receiptOptions"
                  option-filter-prop="label"
                  placeholder="选择可认领到款"
                  class="w-full"
                />
              </div>
              <div>
                <div class="mb-1 text-xs text-gray-500">商务单</div>
                <Select
                  v-model:value="item.businessOrderId"
                  show-search
                  allow-clear
                  :loading="loadingOptions"
                  :options="orderOptions"
                  option-filter-prop="label"
                  placeholder="选择可认领商务单"
                  class="w-full"
                />
              </div>
              <div>
                <div class="mb-1 text-xs text-gray-500">认领金额</div>
                <InputNumber
                  v-model:value="item.claimAmount"
                  :min="0.01"
                  :precision="2"
                  class="w-full"
                  placeholder="本次核销金额"
                />
              </div>
            </div>
          </div>
          <Space>
            <Button type="dashed" @click="addLine">添加明细行</Button>
          </Space>
        </div>
      </Form.Item>
    </Form>
  </Modal>
</template>
