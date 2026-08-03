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
  getSourceInvoiceApplicationPage,
  getSourceReceiptPage,
  updateClaim,
} from '#/api/finance/receipt-claim';

defineOptions({ name: 'FinanceReceiptClaimForm' });

const emit = defineEmits(['success']);

/** 0=待确认 2=已驳回（与 BE 一致） */
const CLAIM_STATUS_PENDING = 0;

interface LineItem {
  receiptId?: number;
  invoiceApplicationId?: number;
  claimAmount?: number;
  claimSource?: string;
}

interface FormData {
  id?: number;
  /** 打开编辑时传入，用于 M2 仅 PENDING 加回 */
  status?: number;
  remark?: string;
  items: LineItem[];
}

const formRef = ref();
const formData = ref<FormData>({ items: [{}] });
/** 打开编辑时的原始明细（PENDING 编辑加回本单占用，M2） */
const originalItems = ref<LineItem[]>([]);
const editStatus = ref<number | undefined>(undefined);
const receiptOptions = ref<
  Array<{ label: string; value: number; claimable: number }>
>([]);
const invoiceOptions = ref<
  Array<{ label: string; value: number; claimable: number }>
>([]);
const loadingOptions = ref(false);

const isEdit = computed(() => !!formData.value.id);
const isPendingEdit = computed(
  () => isEdit.value && editStatus.value === CLAIM_STATUS_PENDING,
);
const getTitle = computed(() =>
  isEdit.value ? '修改到款认领' : '新建到款认领',
);

/** 服务端 claimable +（仅 PENDING）本单原占用 − 本单其他行草稿占用 */
function displayClaimable(
  sourceKind: 'receipt' | 'invoice',
  sourceId: number | undefined,
  rowIndex: number,
): number {
  if (sourceId == null) return 0;
  const server =
    sourceKind === 'receipt'
      ? (receiptOptions.value.find((o) => o.value === sourceId)?.claimable ?? 0)
      : (invoiceOptions.value.find((o) => o.value === sourceId)?.claimable ?? 0);

  // M2：仅 PENDING 时服务端 pending 已含本单 → 加回本单原占用；REJECTED 已释放不加
  let originalOccupy = 0;
  if (isPendingEdit.value) {
    for (const item of originalItems.value) {
      const id =
        sourceKind === 'receipt' ? item.receiptId : item.invoiceApplicationId;
      if (id === sourceId) {
        originalOccupy += Number(item.claimAmount ?? 0);
      }
    }
  }

  let draftOther = 0;
  formData.value.items.forEach((item, idx) => {
    if (idx === rowIndex) return;
    const id =
      sourceKind === 'receipt' ? item.receiptId : item.invoiceApplicationId;
    if (id === sourceId) {
      draftOther += Number(item.claimAmount ?? 0);
    }
  });

  return Math.max(0, server + originalOccupy - draftOther);
}

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
          if (!item.invoiceApplicationId) {
            return Promise.reject(`第 ${idx + 1} 行：请选择开票申请`);
          }
          if (!item.claimAmount || item.claimAmount <= 0) {
            return Promise.reject(`第 ${idx + 1} 行：认领金额须大于 0`);
          }
          const maxReceipt = displayClaimable('receipt', item.receiptId, idx);
          if (item.claimAmount > maxReceipt + 1e-9) {
            return Promise.reject(
              `第 ${idx + 1} 行：到款可认领不足（剩余 ¥${maxReceipt.toFixed(2)}）`,
            );
          }
          const maxInvoice = displayClaimable(
            'invoice',
            item.invoiceApplicationId,
            idx,
          );
          if (item.claimAmount > maxInvoice + 1e-9) {
            return Promise.reject(
              `第 ${idx + 1} 行：开票可认领不足（剩余 ¥${maxInvoice.toFixed(2)}）`,
            );
          }
        }
        return Promise.resolve();
      },
      trigger: 'change',
    },
  ],
};

function mergeSourceOption(
  list: Array<{ label: string; value: number; claimable: number }>,
  id: number | undefined,
  kind: 'receipt' | 'invoice',
) {
  if (id == null) return list;
  if (list.some((o) => o.value === id)) return list;
  return [
    {
      value: id,
      claimable: 0,
      label:
        kind === 'receipt'
          ? `到款 #${id} | 可认领 ¥0.00（本单占用/已选）`
          : `开票 #${id} | 可认领 ¥0.00（本单占用/已选）`,
    },
    ...list,
  ];
}

async function loadSourceOptions() {
  loadingOptions.value = true;
  try {
    const [receiptPage, invoicePage] = await Promise.all([
      getSourceReceiptPage({ pageNo: 1, pageSize: 100 }),
      getSourceInvoiceApplicationPage({ pageNo: 1, pageSize: 100 }),
    ]);
    let receipts = (receiptPage?.list || []).map((r: any) => {
      const claimable = Number(
        r.claimableAmount ??
          Math.max(
            0,
            Number(r.unclaimedAmount ?? 0) - Number(r.pendingClaimedAmount ?? 0),
          ),
      );
      return {
        value: r.id as number,
        claimable,
        label: `${r.receiptNo || r.id} | ${r.payerName || '-'} | 可认领 ¥${claimable.toFixed(2)}`,
      };
    });
    let invoices = (invoicePage?.list || []).map((a: any) => {
      const claimable = Number(
        a.claimableAmount ??
          Math.max(
            0,
            Number(a.totalAmount ?? 0) -
              Number(a.confirmedClaimedAmount ?? 0) -
              Number(a.pendingClaimedAmount ?? 0),
          ),
      );
      return {
        value: a.id as number,
        claimable,
        label: `${a.applicationNo || a.id} | ${a.buyerName || '-'} | 可认领 ¥${claimable.toFixed(2)}`,
      };
    });

    // 编辑态：强制并入本单已选源（即使服务端 claimable=0）
    for (const item of originalItems.value) {
      receipts = mergeSourceOption(receipts, item.receiptId, 'receipt');
      invoices = mergeSourceOption(
        invoices,
        item.invoiceApplicationId,
        'invoice',
      );
    }
    for (const item of formData.value.items || []) {
      receipts = mergeSourceOption(receipts, item.receiptId, 'receipt');
      invoices = mergeSourceOption(
        invoices,
        item.invoiceApplicationId,
        'invoice',
      );
    }

    receiptOptions.value = receipts;
    invoiceOptions.value = invoices;
  } finally {
    loadingOptions.value = false;
  }
}

function receiptSelectOptions(rowIndex: number) {
  return receiptOptions.value.map((o) => {
    const remaining = displayClaimable('receipt', o.value, rowIndex);
    const selected = formData.value.items[rowIndex]?.receiptId === o.value;
    return {
      value: o.value,
      label: o.label.replace(
        /可认领 ¥[\d.]+/,
        `可认领 ¥${remaining.toFixed(2)}`,
      ),
      disabled: remaining <= 0 && !selected,
    };
  });
}

function invoiceSelectOptions(rowIndex: number) {
  return invoiceOptions.value.map((o) => {
    const remaining = displayClaimable('invoice', o.value, rowIndex);
    const selected =
      formData.value.items[rowIndex]?.invoiceApplicationId === o.value;
    return {
      value: o.value,
      label: o.label.replace(
        /可认领 ¥[\d.]+/,
        `可认领 ¥${remaining.toFixed(2)}`,
      ),
      disabled: remaining <= 0 && !selected,
    };
  });
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
  originalItems.value = [];
  editStatus.value = undefined;
  formRef.value?.resetFields();
}

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      return;
    }
    const data = modalApi.getData<FormData>() || {};
    if (
      data.items?.some(
        (i) =>
          i.claimSource === 'LEGACY_BO' ||
          (!i.invoiceApplicationId && (i as any).businessOrderId),
      )
    ) {
      message.warning('历史商务单认领禁止修改');
      modalApi.close();
      return;
    }
    const items =
      data.items && data.items.length
        ? data.items.map((i) => ({
            receiptId: i.receiptId,
            invoiceApplicationId: i.invoiceApplicationId,
            claimAmount: i.claimAmount,
          }))
        : [{}];
    formData.value = {
      id: data.id,
      status: data.status,
      remark: data.remark,
      items,
    };
    editStatus.value = data.status;
    originalItems.value = data.id ? items.map((i) => ({ ...i })) : [];
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
          invoiceApplicationId: i.invoiceApplicationId as number,
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
                <div class="mb-1 text-xs text-gray-500">
                  银行到款
                  <span v-if="item.receiptId" class="text-gray-400">
                    （本行可用 ¥{{
                      displayClaimable('receipt', item.receiptId, index).toFixed(
                        2,
                      )
                    }}）
                  </span>
                </div>
                <Select
                  v-model:value="item.receiptId"
                  show-search
                  allow-clear
                  :loading="loadingOptions"
                  :options="receiptSelectOptions(index)"
                  option-filter-prop="label"
                  placeholder="选择可认领到款"
                  class="w-full"
                />
              </div>
              <div>
                <div class="mb-1 text-xs text-gray-500">
                  开票申请
                  <span v-if="item.invoiceApplicationId" class="text-gray-400">
                    （本行可用 ¥{{
                      displayClaimable(
                        'invoice',
                        item.invoiceApplicationId,
                        index,
                      ).toFixed(2)
                    }}）
                  </span>
                </div>
                <Select
                  v-model:value="item.invoiceApplicationId"
                  show-search
                  allow-clear
                  :loading="loadingOptions"
                  :options="invoiceSelectOptions(index)"
                  option-filter-prop="label"
                  placeholder="选择已审批通过的开票申请（未出票也可）"
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
