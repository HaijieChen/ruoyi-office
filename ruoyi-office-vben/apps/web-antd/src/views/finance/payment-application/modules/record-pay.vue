<script lang="ts" setup>
import { computed, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import {
  DatePicker,
  Form,
  Input,
  InputNumber,
  Select,
  message,
} from 'ant-design-vue';
import dayjs, { type Dayjs } from 'dayjs';

import {
  getPaymentApplication,
  recordPayPaymentApplication,
} from '#/api/finance/payment-application';
import { getCompanyBankAccountSimpleList } from '#/api/finance/company-bank-account';
import { FileUpload } from '#/components/upload';

defineOptions({ name: 'FinancePaymentRecordPay' });

const emit = defineEmits(['success']);

const form = ref<{
  id?: number;
  taskId?: string;
  companyBankAccountId?: number;
  payAmount?: number;
  actualPayDate?: Dayjs;
  payVoucherUrl?: string;
  erpVoucherNo?: string;
  idempotencyKey?: string;
  entityCompanyDeptId?: number;
  applyAmount?: number;
  paidLineSum?: number;
  applicationKind?: string;
  salaryLines?: any[];
  taxLines?: any[];
  entityCompanyName?: string;
}>({});

const accountOptions = ref<{ label: string; value: number }[]>([]);

const payEntityOptions = computed(() => {
  const f = form.value;
  const kind = f.applicationKind || 'ORDINARY';
  if (kind === 'SALARY' && f.salaryLines?.length) {
    const map = new Map<number, string>();
    for (const l of f.salaryLines) {
      if (l.entityCompanyDeptId != null) {
        map.set(
          l.entityCompanyDeptId,
          l.entityCompanyName || String(l.entityCompanyDeptId),
        );
      }
    }
    return [...map.entries()].map(([value, label]) => ({ value, label }));
  }
  if (kind === 'TAX' && f.taxLines?.length) {
    const map = new Map<number, string>();
    for (const l of f.taxLines) {
      if (l.entityCompanyDeptId != null) {
        map.set(
          l.entityCompanyDeptId,
          l.entityCompanyName || String(l.entityCompanyDeptId),
        );
      }
    }
    return [...map.entries()].map(([value, label]) => ({ value, label }));
  }
  if (f.entityCompanyDeptId != null) {
    return [
      {
        value: f.entityCompanyDeptId,
        label: f.entityCompanyName || String(f.entityCompanyDeptId),
      },
    ];
  }
  return [];
});

const multiEntity = computed(() => payEntityOptions.value.length > 1);

const remaining = computed(() => {
  const apply = Number(form.value.applyAmount || 0);
  const paid = Number(form.value.paidLineSum || 0);
  return Math.max(0, +(apply - paid).toFixed(2));
});

function onVoucherUpload(val: string | string[]) {
  const arr = Array.isArray(val) ? val : val ? [val] : [];
  form.value.payVoucherUrl = arr[0] || '';
}

async function loadAccounts(entityCompanyDeptId?: number) {
  accountOptions.value = [];
  form.value.companyBankAccountId = undefined;
  if (!entityCompanyDeptId) return;
  try {
    const list = await getCompanyBankAccountSimpleList(entityCompanyDeptId);
    accountOptions.value = (list || []).map((a) => ({
      label: `${a.accountName} / ${a.bankName} / ${a.accountNoMasked || ''}`,
      value: a.id,
    }));
  } catch {
    accountOptions.value = [];
  }
}

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) return;
    const data = modalApi.getData<{ id?: number; taskId?: string }>() || {};
    form.value = {
      id: data.id,
      taskId: data.taskId || '',
      actualPayDate: dayjs(),
      idempotencyKey:
        (globalThis.crypto?.randomUUID?.() as string) ||
        `pay-${Date.now()}-${Math.random().toString(36).slice(2)}`,
    };
    if (data.id) {
      try {
        const app = await getPaymentApplication(data.id);
        form.value.entityCompanyDeptId = app.entityCompanyDeptId;
        form.value.entityCompanyName = app.entityCompanyName;
        form.value.applyAmount = Number(app.applyAmount || 0);
        form.value.paidLineSum = Number((app as any).paidLineSum || 0);
        form.value.applicationKind = (app as any).applicationKind;
        form.value.salaryLines = (app as any).salaryLines;
        form.value.taxLines = (app as any).taxLines;
        form.value.payAmount = remaining.value || form.value.applyAmount;
        const entities = payEntityOptions.value;
        form.value.entityCompanyDeptId =
          entities[0]?.value ?? app.entityCompanyDeptId;
        await loadAccounts(form.value.entityCompanyDeptId);
      } catch {
        // ignore
      }
    }
  },
  async onConfirm() {
    if (!form.value.id || !form.value.taskId) {
      message.error('缺少申请 id 或任务 id（待办入口请带 taskId）');
      return;
    }
    if (!form.value.companyBankAccountId) {
      message.error('请选择付款账户');
      return;
    }
    if (!form.value.actualPayDate || !form.value.payVoucherUrl) {
      message.error('支付日与支付凭证必填');
      return;
    }
    modalApi.lock();
    try {
      await recordPayPaymentApplication({
        id: form.value.id,
        taskId: form.value.taskId,
        companyBankAccountId: form.value.companyBankAccountId,
        payAmount: form.value.payAmount,
        actualPayDate: form.value.actualPayDate.format('YYYY-MM-DD'),
        payVoucherUrl: form.value.payVoucherUrl,
        erpVoucherNo: form.value.erpVoucherNo,
        idempotencyKey: form.value.idempotencyKey!,
      });
      message.success('出纳支付已登记');
      emit('success');
      modalApi.close();
    } finally {
      modalApi.unlock();
    }
  },
});

watch(
  () => form.value.entityCompanyDeptId,
  (v) => loadAccounts(v),
);
</script>

<template>
  <Modal title="出纳支付办结" class="w-[560px]">
    <Form :label-col="{ span: 7 }" :wrapper-col="{ span: 15 }">
      <Form.Item label="任务 ID" required>
        <Input v-model:value="form.taskId" placeholder="BPM 待办 taskId" />
      </Form.Item>
      <Form.Item v-if="multiEntity" label="付款主体" required>
        <Select
          v-model:value="form.entityCompanyDeptId"
          :options="payEntityOptions"
          placeholder="按明细主体选择"
        />
      </Form.Item>
      <Form.Item label="付款账户" required>
        <Select
          v-model:value="form.companyBankAccountId"
          :options="accountOptions"
          show-search
          option-filter-prop="label"
          placeholder="仅显示该主体启用账户（账号脱敏）"
        />
      </Form.Item>
      <Form.Item label="本笔金额" required>
        <InputNumber
          v-model:value="form.payAmount"
          class="w-full"
          :min="0.01"
          :max="remaining || undefined"
          :precision="2"
        />
        <div class="text-xs text-gray-500">
          剩余可付 {{ remaining }}
        </div>
      </Form.Item>
      <Form.Item label="实际支付日期" required>
        <DatePicker v-model:value="form.actualPayDate" class="w-full" />
      </Form.Item>
      <Form.Item label="支付凭证" required>
        <FileUpload
          :value="form.payVoucherUrl ? [form.payVoucherUrl] : []"
          :max-number="1"
          :max-size="20"
          :multiple="false"
          help-text="上传回单/截图"
          @update:value="onVoucherUpload"
        />
      </Form.Item>
      <Form.Item label="ERP 凭证号">
        <Input v-model:value="form.erpVoucherNo" />
      </Form.Item>
    </Form>
  </Modal>
</template>
