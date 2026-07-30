<script lang="ts" setup>
import type { Rule } from 'ant-design-vue/es/form';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import {
  Button,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Textarea,
  message,
} from 'ant-design-vue';

import {
  getBusinessOrder,
  getBusinessOrderPage,
} from '#/api/finance/business-order';
import {
  createAndStartInvoiceApplication,
  getInvoiceApplication,
  resubmitInvoiceApplication,
} from '#/api/finance/invoice-application';

defineOptions({ name: 'FinanceInvoiceApplicationForm' });

const emit = defineEmits(['success']);

interface LineItem {
  businessOrderId?: number;
  amount?: number;
  billingPeriod?: string;
}

interface FormData {
  id?: number;
  mode?: 'create' | 'resubmit';
  buyerName?: string;
  buyerTaxNo?: string;
  invoiceCompany?: string;
  invoiceType?: string;
  remark?: string;
  lines: LineItem[];
}

interface BoOption {
  label: string;
  value: number;
  remainingBalance?: number;
  settlementAmount?: number;
  orderNo?: string;
}

const formRef = ref();
const formData = ref<FormData>({ lines: [{}] });
const boOptions = ref<BoOption[]>([]);
const loadingBo = ref(false);
/** orderNo 远程搜索防抖 */
let boSearchTimer: ReturnType<typeof setTimeout> | undefined;

const isResubmit = computed(() => formData.value.mode === 'resubmit');
const getTitle = computed(() =>
  isResubmit.value ? '驳回后重提开票申请' : '提交开票申请（无草稿）',
);

const rules: Record<string, Rule[]> = {
  buyerName: [{ required: true, message: '购方名称不能为空' }],
  lines: [
    {
      validator: async () => {
        const lines = formData.value.lines || [];
        if (!lines.length) return Promise.reject('请至少添加一行明细');
        for (const [idx, line] of lines.entries()) {
          if (!line.businessOrderId) {
            return Promise.reject(`第 ${idx + 1} 行：请选择商务单`);
          }
          if (!line.amount || line.amount <= 0) {
            return Promise.reject(`第 ${idx + 1} 行：金额须大于 0`);
          }
        }
        return Promise.resolve();
      },
    },
  ],
};

function formatBoLabel(bo: {
  id: number;
  orderNo?: string;
  productName?: string;
  payerName?: string;
  remainingBalance?: number;
  settlementAmount?: number;
}) {
  const remain = Number(bo.remainingBalance ?? bo.settlementAmount ?? 0);
  return `${bo.orderNo || bo.id} | ${bo.productName || '-'} | ${bo.payerName || '-'} | 余额 ¥${remain.toFixed(2)}`;
}

function upsertBoOption(opt: BoOption) {
  const list = boOptions.value.filter((o) => o.value !== opt.value);
  list.unshift(opt);
  boOptions.value = list;
}

async function loadBusinessOrderOptions(keyword?: string) {
  loadingBo.value = true;
  try {
    const page = await getBusinessOrderPage({
      pageNo: 1,
      pageSize: 50,
      orderNo: keyword?.trim() || undefined,
    });
    const mapped = (page?.list || []).map((bo: any) => ({
      value: bo.id as number,
      orderNo: bo.orderNo as string,
      remainingBalance: Number(bo.remainingBalance ?? 0),
      settlementAmount: Number(bo.settlementAmount ?? 0),
      label: formatBoLabel(bo),
    }));
    // 保留已选但不在当前页的选项，避免重提/筛选后丢 label
    const selectedIds = new Set(
      (formData.value.lines || [])
        .map((l) => l.businessOrderId)
        .filter((id): id is number => !!id),
    );
    const keep = boOptions.value.filter(
      (o) => selectedIds.has(o.value) && !mapped.some((m) => m.value === o.value),
    );
    boOptions.value = [...mapped, ...keep];
  } finally {
    loadingBo.value = false;
  }
}

function onBoSearch(keyword: string) {
  if (boSearchTimer) clearTimeout(boSearchTimer);
  boSearchTimer = setTimeout(() => {
    void loadBusinessOrderOptions(keyword);
  }, 300);
}

/** 选中商务单：回填建议金额（余额优先，否则结算金额） */
async function onBoChange(index: number, boId?: number) {
  if (!boId) return;
  const line = formData.value.lines[index];
  if (!line) return;
  let opt = boOptions.value.find((o) => o.value === boId);
  if (!opt) {
    try {
      const bo = await getBusinessOrder(boId);
      opt = {
        value: bo.id,
        orderNo: bo.orderNo,
        remainingBalance: Number(bo.remainingBalance ?? 0),
        settlementAmount: Number(bo.settlementAmount ?? 0),
        label: formatBoLabel(bo),
      };
      upsertBoOption(opt);
    } catch {
      return;
    }
  }
  if (line.amount == null || line.amount <= 0) {
    const suggest =
      Number(opt.remainingBalance) > 0
        ? Number(opt.remainingBalance)
        : Number(opt.settlementAmount) || undefined;
    if (suggest && suggest > 0) {
      line.amount = Number(suggest.toFixed(2));
    }
  }
}

/** 重提时：补全当前行已选商务单的下拉项 */
async function ensureSelectedBoOptions(lines: LineItem[]) {
  const ids = [
    ...new Set(
      lines.map((l) => l.businessOrderId).filter((id): id is number => !!id),
    ),
  ];
  await Promise.all(
    ids.map(async (id) => {
      if (boOptions.value.some((o) => o.value === id)) return;
      try {
        const bo = await getBusinessOrder(id);
        upsertBoOption({
          value: bo.id,
          orderNo: bo.orderNo,
          remainingBalance: Number(bo.remainingBalance ?? 0),
          settlementAmount: Number(bo.settlementAmount ?? 0),
          label: formatBoLabel(bo),
        });
      } catch {
        upsertBoOption({
          value: id,
          label: `商务单 #${id}`,
        });
      }
    }),
  );
}

function addLine() {
  formData.value.lines.push({});
}

function removeLine(index: number) {
  if (formData.value.lines.length <= 1) {
    message.warning('至少保留一行');
    return;
  }
  formData.value.lines.splice(index, 1);
}

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) return;
    const data = modalApi.getData<FormData>() || {};
    await loadBusinessOrderOptions();
    if (data.id && data.mode === 'resubmit') {
      const detail = await getInvoiceApplication(data.id);
      formData.value = {
        id: detail.id,
        mode: 'resubmit',
        buyerName: detail.buyerName,
        buyerTaxNo: detail.buyerTaxNo,
        invoiceCompany: detail.invoiceCompany,
        invoiceType: detail.invoiceType,
        remark: detail.remark as any,
        lines: (detail.lines || []).map((l) => ({
          businessOrderId: l.businessOrderId,
          amount: Number(l.amount),
          billingPeriod: l.billingPeriod,
        })),
      };
      if (!formData.value.lines.length) {
        formData.value.lines = [{}];
      }
      await ensureSelectedBoOptions(formData.value.lines);
    } else {
      formData.value = { mode: 'create', lines: [{}] };
    }
  },
  async onConfirm() {
    await formRef.value?.validate();
    modalApi.lock();
    try {
      const payload = {
        buyerName: formData.value.buyerName as string,
        buyerTaxNo: formData.value.buyerTaxNo,
        invoiceCompany: formData.value.invoiceCompany,
        invoiceType: formData.value.invoiceType,
        remark: formData.value.remark,
        lines: formData.value.lines.map((l) => ({
          businessOrderId: l.businessOrderId as number,
          amount: l.amount as number,
          billingPeriod: l.billingPeriod,
        })),
      };
      if (isResubmit.value && formData.value.id) {
        await resubmitInvoiceApplication(formData.value.id, payload);
        message.success('已重提并启动新审批');
      } else {
        await createAndStartInvoiceApplication(payload);
        message.success('已提交并启动审批（无草稿）');
      }
      emit('success');
      modalApi.close();
    } finally {
      modalApi.unlock();
    }
  },
  onClosed() {
    formData.value = { lines: [{}] };
    boOptions.value = [];
    if (boSearchTimer) clearTimeout(boSearchTimer);
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-[800px]">
    <div class="mb-3 rounded bg-blue-50 px-3 py-2 text-sm text-blue-800">
      仅支持「提交即启流」。无草稿**按钮。审批在 BPM 完成；办票在审批通过后单独办理。
    </div>
    <Form
      ref="formRef"
      :model="formData"
      :rules="rules"
      :label-col="{ span: 5 }"
      :wrapper-col="{ span: 18 }"
    >
      <Form.Item label="购方名称" name="buyerName" required>
        <Input v-model:value="formData.buyerName" placeholder="提交快照" />
      </Form.Item>
      <Form.Item label="购方税号" name="buyerTaxNo">
        <Input v-model:value="formData.buyerTaxNo" />
      </Form.Item>
      <Form.Item label="开票公司" name="invoiceCompany">
        <Input v-model:value="formData.invoiceCompany" />
      </Form.Item>
      <Form.Item label="发票类型" name="invoiceType">
        <Input v-model:value="formData.invoiceType" placeholder="普票/专票" />
      </Form.Item>
      <Form.Item label="备注" name="remark">
        <Textarea v-model:value="formData.remark" :rows="2" />
      </Form.Item>
      <Form.Item label="明细" name="lines" required>
        <div class="space-y-2">
          <div
            v-for="(line, index) in formData.lines"
            :key="index"
            class="rounded border p-2"
          >
            <div class="mb-1 flex justify-between text-xs text-gray-500">
              <span>行 {{ index + 1 }}</span>
              <Button type="link" danger size="small" @click="removeLine(index)">
                删除
              </Button>
            </div>
            <div class="grid grid-cols-1 gap-2 sm:grid-cols-3">
              <div class="sm:col-span-1">
                <div class="mb-1 text-xs text-gray-500">商务单</div>
                <Select
                  v-model:value="line.businessOrderId"
                  class="w-full"
                  show-search
                  allow-clear
                  :loading="loadingBo"
                  :options="boOptions"
                  option-filter-prop="label"
                  placeholder="搜索单号并选择"
                  :filter-option="false"
                  @search="onBoSearch"
                  @change="(v: any) => onBoChange(index, v)"
                  @dropdown-visible-change="
                    (open: boolean) => {
                      if (open && !boOptions.length) loadBusinessOrderOptions();
                    }
                  "
                />
              </div>
              <div>
                <div class="mb-1 text-xs text-gray-500">开票金额</div>
                <InputNumber
                  v-model:value="line.amount"
                  class="w-full"
                  placeholder="金额"
                  :min="0.01"
                  :precision="2"
                />
              </div>
              <div>
                <div class="mb-1 text-xs text-gray-500">账期</div>
                <Input
                  v-model:value="line.billingPeriod"
                  placeholder="账期 YYYY-MM"
                />
              </div>
            </div>
          </div>
          <Space>
            <Button type="dashed" @click="addLine">添加行</Button>
          </Space>
        </div>
      </Form.Item>
    </Form>
  </Modal>
</template>
