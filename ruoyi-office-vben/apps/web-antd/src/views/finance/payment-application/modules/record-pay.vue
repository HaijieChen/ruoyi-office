<script lang="ts" setup>
import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import {
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Select,
  Switch,
  Table,
  message,
} from 'ant-design-vue';
import dayjs, { type Dayjs } from 'dayjs';

import {
  getPaymentApplication,
  ocrPaymentVoucher,
  recordPayPaymentApplication,
} from '#/api/finance/payment-application';
import { getCompanyBankAccountSimpleList } from '#/api/finance/company-bank-account';
import { FileUpload } from '#/components/upload';
import { useUpload } from '#/components/upload/use-upload';

defineOptions({ name: 'FinancePaymentRecordPay' });

const emit = defineEmits(['success']);

interface ReceiptRow {
  key: string;
  companyBankAccountId?: number;
  payAmount?: number;
  actualPayDate?: Dayjs;
  payVoucherUrl?: string;
  erpVoucherNo?: string;
  idempotencyKey: string;
}

const form = ref<{
  id?: number;
  taskId?: string;
  entityCompanyDeptId?: number;
  applyAmount?: number;
  paidLineSum?: number;
  entityCompanyName?: string;
  materialsComplete?: boolean;
}>({ materialsComplete: true });

const rows = ref<ReceiptRow[]>([]);
const accountOptions = ref<{ label: string; value: number }[]>([]);
const { httpRequest } = useUpload();

const remaining = computed(() => {
  const apply = Number(form.value.applyAmount || 0);
  const paid = Number(form.value.paidLineSum || 0);
  return Math.max(0, +(apply - paid).toFixed(2));
});

const draftSum = computed(() =>
  rows.value.reduce((sum, row) => sum + Number(row.payAmount || 0), 0),
);

function unwrapOcr(raw: any) {
  if (!raw || typeof raw !== 'object') return raw;
  if (raw.amount != null || raw.feeDate) return raw;
  if (raw.data && typeof raw.data === 'object') return raw.data;
  return raw;
}

function rawUploadFile(file: File) {
  const inner = (file as any)?.originFileObj;
  return inner instanceof Blob ? inner : file;
}

function newRow(): ReceiptRow {
  return {
    key:
      (globalThis.crypto?.randomUUID?.() as string) ||
      `pay-${Date.now()}-${Math.random().toString(36).slice(2)}`,
    actualPayDate: dayjs(),
    idempotencyKey:
      (globalThis.crypto?.randomUUID?.() as string) ||
      `idem-${Date.now()}-${Math.random().toString(36).slice(2)}`,
  };
}

function addRow() {
  rows.value.push(newRow());
}

function removeRow(key: string) {
  if (rows.value.length <= 1) {
    message.warning('至少保留一行回执');
    return;
  }
  rows.value = rows.value.filter((row) => row.key !== key);
}

async function uploadRowVoucherAndOcr(
  row: ReceiptRow,
  file: File,
  onUploadProgress?: any,
) {
  const raw = rawUploadFile(file);
  const hide = message.loading({
    content: '正在识别回单金额、流水号、日期...',
    duration: 0,
  });
  try {
    let amount: number | undefined;
    let feeDate: string | undefined;
    let serialNo: string | undefined;
    function applyOcr(ocr: any) {
      if (ocr?.amount != null && !Number.isNaN(Number(ocr.amount))) {
        amount = Number(ocr.amount);
      }
      if (ocr?.feeDate) feeDate = String(ocr.feeDate).slice(0, 10);
      serialNo = ocr?.invoiceNo || ocr?.serialNo || serialNo;
    }
    if (raw instanceof Blob) {
      applyOcr(unwrapOcr(await ocrPaymentVoucher('', raw as File)));
    }
    const res = await httpRequest(raw, onUploadProgress);
    const url =
      typeof res === 'string'
        ? res
        : String((res as any)?.url || (res as any)?.data || '');
    if ((amount == null || !feeDate || !serialNo) && url) {
      applyOcr(unwrapOcr(await ocrPaymentVoucher(url)));
    }
    if (feeDate && dayjs(feeDate).isValid()) {
      row.actualPayDate = dayjs(feeDate);
    }
    if (serialNo) {
      row.erpVoucherNo = serialNo;
    }
    if (amount != null) {
      const other = draftSum.value - Number(row.payAmount || 0);
      if (other + amount - remaining.value > 1e-9) {
        message.error(
          `回单金额 ¥${amount.toFixed(2)} 会使合计超过剩余可付 ¥${remaining.value.toFixed(2)}，已拦截`,
        );
        throw new Error('OCR amount exceed remaining');
      }
      row.payAmount = amount;
      message.success('已识别金额、流水号、日期，请核对');
    } else {
      message.warning('未识别到金额，请手填');
    }
    return res;
  } finally {
    hide();
  }
}

function onRowVoucherUpload(row: ReceiptRow, val: string | string[]) {
  const arr = Array.isArray(val) ? val : val ? [val] : [];
  row.payVoucherUrl = arr[0] || '';
}

async function loadAccounts(entityCompanyDeptId?: number) {
  accountOptions.value = [];
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
      materialsComplete: true,
    };
    rows.value = [newRow()];
    if (data.id) {
      try {
        const app = await getPaymentApplication(data.id);
        form.value.entityCompanyDeptId = app.entityCompanyDeptId;
        form.value.entityCompanyName = app.entityCompanyName;
        form.value.applyAmount = Number(app.applyAmount || 0);
        form.value.paidLineSum = Number((app as any).paidLineSum || 0);
        await loadAccounts(form.value.entityCompanyDeptId);
      } catch {
        // ignore
      }
    }
  },
  async onConfirm() {
    if (!form.value.id) {
      message.error('缺少申请 id');
      return;
    }
    const lines = rows.value.map((row) => ({
      companyBankAccountId: row.companyBankAccountId!,
      payAmount: Number(row.payAmount || 0),
      actualPayDate: row.actualPayDate?.format('YYYY-MM-DD') || '',
      payVoucherUrl: row.payVoucherUrl || '',
      erpVoucherNo: row.erpVoucherNo,
      idempotencyKey: row.idempotencyKey,
    }));
    if (
      lines.some(
        (line) =>
          !line.companyBankAccountId ||
          !line.payAmount ||
          line.payAmount <= 0 ||
          !line.actualPayDate ||
          !line.payVoucherUrl,
      )
    ) {
      message.error('每行须填写账户、金额、日期和回单');
      return;
    }
    const sum = lines.reduce((acc, line) => acc + line.payAmount, 0);
    if (sum - remaining.value > 1e-9) {
      message.error(
        `支付金额 ¥${sum.toFixed(2)} 超过剩余可付 ¥${remaining.value.toFixed(2)}，已拦截`,
      );
      return;
    }
    modalApi.lock();
    try {
      await recordPayPaymentApplication({
        id: form.value.id,
        taskId: form.value.taskId || undefined,
        lines,
        materialsComplete: form.value.materialsComplete !== false,
      });
      message.success(
        sum + 1e-9 >= remaining.value ? '已全部付款' : '已登记部分付款',
      );
      emit('success');
      modalApi.close();
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal title="支付" class="w-[980px]">
    <div class="mb-3 text-xs text-gray-500">
      申请金额 {{ form.applyAmount ?? 0 }}，已付 {{ form.paidLineSum ?? 0 }}，剩余可付
      {{ remaining }}，本表合计 {{ draftSum.toFixed(2) }}
    </div>
    <Table
      :data-source="rows"
      :pagination="false"
      size="small"
      row-key="key"
      bordered
    >
      <Table.Column title="付款账户" width="200">
        <template #default="{ record }">
          <Select
            v-model:value="record.companyBankAccountId"
            class="w-full"
            :options="accountOptions"
            show-search
            option-filter-prop="label"
            placeholder="选择账户"
          />
        </template>
      </Table.Column>
      <Table.Column title="银行回单" width="180">
        <template #default="{ record }">
          <FileUpload
            :value="record.payVoucherUrl ? [record.payVoucherUrl] : []"
            :max-number="1"
            :max-size="20"
            :multiple="false"
            :accept="['pdf', 'jpg', 'jpeg', 'png']"
            :api="(file, progress) => uploadRowVoucherAndOcr(record, file as File, progress)"
            @update:value="(val) => onRowVoucherUpload(record, val)"
          />
        </template>
      </Table.Column>
      <Table.Column title="金额" width="110">
        <template #default="{ record }">
          <InputNumber
            v-model:value="record.payAmount"
            class="w-full"
            :min="0.01"
            :precision="2"
          />
        </template>
      </Table.Column>
      <Table.Column title="日期" width="140">
        <template #default="{ record }">
          <DatePicker v-model:value="record.actualPayDate" class="w-full" />
        </template>
      </Table.Column>
      <Table.Column title="流水号" width="140">
        <template #default="{ record }">
          <Input v-model:value="record.erpVoucherNo" placeholder="OCR 回填" />
        </template>
      </Table.Column>
      <Table.Column title="" width="70">
        <template #default="{ record }">
          <Button type="link" danger @click="removeRow(record.key)">删除</Button>
        </template>
      </Table.Column>
    </Table>
    <Button class="mt-2" @click="addRow">新增回执行</Button>
    <Form class="mt-4" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
      <Form.Item label="资料/发票完整">
        <Switch v-model:checked="form.materialsComplete" />
        <div class="text-xs text-gray-500">关闭则付款已登记但单据为待补票</div>
      </Form.Item>
    </Form>
  </Modal>
</template>
