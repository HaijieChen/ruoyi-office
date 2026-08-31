<script lang="ts" setup>
import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import {
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Table,
  message,
} from 'ant-design-vue';

import type { FinanceInvoiceApplicationApi } from '#/api/finance/invoice-application';
import {
  completeInvoiceIssue,
  getInvoiceApplication,
  ocrInvoiceApplication,
} from '#/api/finance/invoice-application';
import { FileUpload } from '#/components/upload';
import { displayDateTime } from '#/utils/display-time';

defineOptions({ name: 'FinanceInvoiceIssueForm' });

const emit = defineEmits(['success']);

interface DraftInvoice {
  url: string;
  name?: string;
  amount?: number;
  invoiceNo?: string;
  invoiceDate?: string;
}

const formData = ref({
  applicationId: undefined as number | undefined,
  applyAmount: 0,
  history: [] as FinanceInvoiceApplicationApi.IssueFile[],
  drafts: [] as DraftInvoice[],
});

const historyIssued = computed(() =>
  (formData.value.history || []).reduce(
    (sum, f) => sum + (Number(f.amount) || 0),
    0,
  ),
);
const draftIssued = computed(() =>
  (formData.value.drafts || []).reduce(
    (sum, f) => sum + (Number(f.amount) || 0),
    0,
  ),
);
const totalIssued = computed(() => historyIssued.value + draftIssued.value);
const remaining = computed(() =>
  Number((formData.value.applyAmount - historyIssued.value).toFixed(2)),
);

const historyColumns = [
  {
    title: '附件',
    dataIndex: 'fileName',
    key: 'fileName',
    ellipsis: true,
    customRender: ({
      record,
    }: {
      record: FinanceInvoiceApplicationApi.IssueFile;
    }) => record.fileName || record.fileUrl || '-',
  },
  {
    title: '金额',
    dataIndex: 'amount',
    key: 'amount',
    width: 110,
    customRender: ({ text }: { text?: number }) =>
      text != null ? `¥${Number(text).toFixed(2)}` : '-',
  },
  {
    title: '发票号',
    dataIndex: 'invoiceNo',
    key: 'invoiceNo',
    width: 140,
    customRender: ({ text }: { text?: string }) => text || '-',
  },
  {
    title: '开票日期',
    dataIndex: 'invoiceDate',
    key: 'invoiceDate',
    width: 120,
    customRender: ({ text }: { text?: string }) => text || '-',
  },
  {
    title: '上传时间',
    dataIndex: 'createTime',
    key: 'createTime',
    width: 160,
    customRender: ({ text }: { text?: string }) => displayDateTime(text) || '-',
  },
];

async function recognize(url: string, file?: File): Promise<DraftInvoice> {
  const path = String(url).split('?')[0] || '';
  const base = path.substring(path.lastIndexOf('/') + 1);
  const draft: DraftInvoice = {
    url,
    name: base || 'invoice',
  };
  try {
    const raw = await ocrInvoiceApplication(url, file);
    const ocr =
      raw && typeof raw === 'object' && (raw as any).amount == null && (raw as any).data
        ? (raw as any).data
        : raw;
    if (ocr?.amount != null) draft.amount = Number(ocr.amount);
    if (ocr?.invoiceNo) draft.invoiceNo = String(ocr.invoiceNo);
    if (ocr?.feeDate) draft.invoiceDate = String(ocr.feeDate).slice(0, 10);
    if (ocr?.invoiceNo || ocr?.feeDate || ocr?.amount != null) {
      message.success('已识别发票信息，请核对');
    } else {
      message.warning('未识别到金额/发票号，请手填');
    }
  } catch {
    message.warning('识别失败，请手填金额和发票号');
  }
  return draft;
}

async function onFileUploadUpdate(val: string | string[], files?: any[]) {
  const list = Array.isArray(val) ? val : val ? [val] : [];
  const existingByUrl = new Map(formData.value.drafts.map((d) => [d.url, d]));
  const next: DraftInvoice[] = [];
  for (const [idx, url] of list.entries()) {
    const kept = existingByUrl.get(url);
    if (kept) {
      next.push(kept);
      continue;
    }
    const hide = message.loading({ content: '正在识别发票...', duration: 0 });
    try {
      next.push(await recognize(url, files?.[idx]));
    } finally {
      hide();
    }
  }
  formData.value.drafts = next;
}

function removeDraft(index: number) {
  const next = [...formData.value.drafts];
  next.splice(index, 1);
  formData.value.drafts = next;
}

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) return;
    const data = modalApi.getData<{ applicationId: number }>() || {};
    formData.value = {
      applicationId: data.applicationId,
      applyAmount: 0,
      history: [],
      drafts: [],
    };
    if (!data.applicationId) return;
    try {
      const detail = await getInvoiceApplication(data.applicationId);
      formData.value.applyAmount = Number(detail.totalAmount || 0);
      formData.value.history = detail.files || [];
    } catch {
      message.warning('加载办票历史失败，仍可上传新发票');
    }
  },
  async onConfirm() {
    if (!formData.value.applicationId) {
      message.warning('缺少开票申请编号');
      return;
    }
    if (!formData.value.drafts.length) {
      message.warning('请至少上传一张发票');
      return;
    }
    for (const [idx, draft] of formData.value.drafts.entries()) {
      if (!draft.amount || draft.amount <= 0) {
        message.warning(`第 ${idx + 1} 张发票请填写金额`);
        return;
      }
    }
    if (totalIssued.value - formData.value.applyAmount > 1e-9) {
      message.error(
        `办票累计 ¥${totalIssued.value.toFixed(2)} 超过申请金额 ¥${formData.value.applyAmount.toFixed(2)}，已拦截`,
      );
      return;
    }
    modalApi.lock();
    try {
      await completeInvoiceIssue({
        applicationId: formData.value.applicationId,
        files: formData.value.drafts.map((d) => ({
          url: d.url,
          name: d.name,
          amount: d.amount as number,
          invoiceNo: d.invoiceNo,
          invoiceDate: d.invoiceDate,
        })),
      });
      message.success(
        totalIssued.value + 1e-9 >= formData.value.applyAmount
          ? '全部办票完成'
          : '已追加部分办票',
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
  <Modal title="办票" class="w-[720px]">
    <div class="mb-3 text-sm text-gray-600">
      申请金额 ¥{{ formData.applyAmount.toFixed(2) }} · 已办票 ¥{{
        historyIssued.toFixed(2)
      }}
      · 剩余可办 ¥{{ Math.max(remaining, 0).toFixed(2) }}
    </div>
    <div v-if="formData.history.length" class="mb-4">
      <div class="mb-2 text-sm font-medium">办票历史</div>
      <Table
        size="small"
        :columns="historyColumns"
        :data-source="formData.history"
        :pagination="false"
        row-key="id"
        bordered
      />
    </div>
    <Form :label-col="{ span: 4 }" :wrapper-col="{ span: 20 }">
      <Form.Item label="本次发票" required>
        <FileUpload
          :value="formData.drafts.map((d) => d.url)"
          :max-number="20"
          :max-size="20"
          :multiple="true"
          help-text="上传后自动识别金额、发票号、开票日期，可改；金额必填"
          @update:value="onFileUploadUpdate"
        />
      </Form.Item>
    </Form>
    <div
      v-for="(draft, index) in formData.drafts"
      :key="draft.url"
      class="mb-2 rounded border p-2"
    >
      <div class="mb-1 flex justify-between text-xs text-gray-500">
        <span>{{ draft.name || draft.url }}</span>
        <Button type="link" danger size="small" @click="removeDraft(index)">
          删除
        </Button>
      </div>
      <div class="grid grid-cols-1 gap-2 sm:grid-cols-3">
        <InputNumber
          v-model:value="draft.amount"
          class="w-full"
          :min="0.01"
          :precision="2"
          placeholder="金额"
        />
        <Input v-model:value="draft.invoiceNo" placeholder="发票号" />
        <DatePicker
          v-model:value="draft.invoiceDate"
          value-format="YYYY-MM-DD"
          class="w-full"
          placeholder="开票日期"
        />
      </div>
    </div>
  </Modal>
</template>
