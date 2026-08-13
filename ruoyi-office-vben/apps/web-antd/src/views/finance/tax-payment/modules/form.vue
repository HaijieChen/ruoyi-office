<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import {
  Button,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  message,
} from 'ant-design-vue';

import {
  createAndStartTaxPayment,
  getTaxPayment,
  resubmitTaxPayment,
} from '#/api/finance/tax-payment';
import { getSimpleDeptList } from '#/api/system/dept';
import { FileUpload } from '#/components/upload';

defineOptions({ name: 'FinanceTaxPaymentForm' });

const emit = defineEmits(['success']);

interface Line {
  entityCompanyDeptId?: number;
  vatAmount?: number;
  surchargeAmount?: number;
  stampTaxAmount?: number;
  citAmount?: number;
}

const form = ref<{
  id?: number;
  paymentTiming?: string;
  periodLabel?: string;
  currency?: string;
  specialNote?: string;
  evidenceFileUrls?: string[];
  lines: Line[];
}>({
  currency: 'CNY',
  paymentTiming: 'IMMEDIATE',
  evidenceFileUrls: [],
  lines: [{}],
});

const companyOptions = ref<{ label: string; value: number }[]>([]);
const isResubmit = computed(() => !!form.value.id);
const title = computed(() =>
  isResubmit.value ? '驳回后重提税金付款' : '新建税金付款申请',
);

onMounted(async () => {
  try {
    const list = await getSimpleDeptList();
    companyOptions.value = (list || [])
      .filter((d: any) => String(d.orgType) === '1')
      .map((d: any) => ({ label: d.name, value: d.id }));
  } catch {
    companyOptions.value = [];
  }
});

function addLine() {
  form.value.lines.push({});
}

function removeLine(idx: number) {
  form.value.lines.splice(idx, 1);
  if (form.value.lines.length === 0) form.value.lines.push({});
}

const total = computed(() =>
  form.value.lines.reduce((s, l) => {
    return (
      s +
      Number(l.vatAmount || 0) +
      Number(l.surchargeAmount || 0) +
      Number(l.stampTaxAmount || 0) +
      Number(l.citAmount || 0)
    );
  }, 0),
);

function parseEvidence(raw?: string | string[]): string[] {
  if (!raw) return [];
  if (Array.isArray(raw)) return raw;
  try {
    const j = JSON.parse(raw);
    return Array.isArray(j) ? j : [];
  } catch {
    return raw ? [raw] : [];
  }
}

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) return;
    const data = modalApi.getData<{ id?: number }>() || {};
    if (data.id) {
      const detail = await getTaxPayment(data.id);
      form.value = {
        id: detail.id,
        paymentTiming: detail.paymentTiming || 'IMMEDIATE',
        periodLabel: (detail as any).periodLabel,
        currency: detail.currency || 'CNY',
        specialNote: detail.specialNote,
        evidenceFileUrls: parseEvidence(detail.evidenceFileUrls as any),
        lines: ((detail as any).taxLines || []).map((l: any) => ({
          entityCompanyDeptId: l.entityCompanyDeptId,
          vatAmount: Number(l.vatAmount || 0),
          surchargeAmount: Number(l.surchargeAmount || 0),
          stampTaxAmount: Number(l.stampTaxAmount || 0),
          citAmount: Number(l.citAmount || 0),
        })),
      };
      if (!form.value.lines.length) form.value.lines = [{}];
    } else {
      form.value = {
        currency: 'CNY',
        paymentTiming: 'IMMEDIATE',
        evidenceFileUrls: [],
        lines: [{}],
      };
    }
  },
  async onConfirm() {
    if (!form.value.periodLabel) {
      message.error('请填写税款所属期');
      return;
    }
    if (!form.value.evidenceFileUrls?.length) {
      message.error('税金依据附件必传');
      return;
    }
    if (!form.value.lines.every((l) => l.entityCompanyDeptId)) {
      message.error('请为每行选择主体公司');
      return;
    }
    const payload = {
      paymentTiming: form.value.paymentTiming || 'IMMEDIATE',
      periodLabel: form.value.periodLabel!,
      currency: form.value.currency || 'CNY',
      specialNote: form.value.specialNote,
      evidenceFileUrls: form.value.evidenceFileUrls!,
      lines: form.value.lines.map((l) => ({
        entityCompanyDeptId: l.entityCompanyDeptId!,
        vatAmount: Number(l.vatAmount || 0),
        surchargeAmount: Number(l.surchargeAmount || 0),
        stampTaxAmount: Number(l.stampTaxAmount || 0),
        citAmount: Number(l.citAmount || 0),
      })),
    };
    modalApi.lock();
    try {
      if (isResubmit.value && form.value.id) {
        await resubmitTaxPayment(form.value.id, payload);
        message.success('已重提税金付款申请');
      } else {
        await createAndStartTaxPayment(payload);
        message.success('已提交税金付款申请');
      }
      emit('success');
      modalApi.close();
    } finally {
      modalApi.unlock();
    }
  },
  onClosed() {
    form.value = {
      currency: 'CNY',
      paymentTiming: 'IMMEDIATE',
      evidenceFileUrls: [],
      lines: [{}],
    };
  },
});
</script>

<template>
  <Modal :title="title" class="w-[900px]">
    <Form :label-col="{ span: 5 }" :wrapper-col="{ span: 18 }">
      <Form.Item label="税款所属期" required>
        <Input v-model:value="form.periodLabel" placeholder="如 2026-Q2" />
      </Form.Item>
      <Form.Item label="支付时效" required>
        <Select
          v-model:value="form.paymentTiming"
          :options="[
            { label: '即时', value: 'IMMEDIATE' },
            { label: '月底', value: 'MONTH_END' },
            { label: '通知', value: 'ON_NOTICE' },
          ]"
        />
      </Form.Item>
      <Form.Item label="币种" required>
        <Select
          v-model:value="form.currency"
          :options="[
            { label: 'CNY', value: 'CNY' },
            { label: 'USD', value: 'USD' },
            { label: 'HKD', value: 'HKD' },
          ]"
        />
      </Form.Item>
      <Form.Item label="明细" required>
        <div
          v-for="(line, idx) in form.lines"
          :key="idx"
          class="mb-2 rounded border p-2"
        >
          <Space wrap>
            <Select
              v-model:value="line.entityCompanyDeptId"
              style="width: 160px"
              :options="companyOptions"
              placeholder="主体公司"
              show-search
              option-filter-prop="label"
            />
            <InputNumber
              v-model:value="line.vatAmount"
              :min="0"
              :precision="2"
              placeholder="增值税"
            />
            <InputNumber
              v-model:value="line.surchargeAmount"
              :min="0"
              :precision="2"
              placeholder="附加税"
            />
            <InputNumber
              v-model:value="line.stampTaxAmount"
              :min="0"
              :precision="2"
              placeholder="印花税"
            />
            <InputNumber
              v-model:value="line.citAmount"
              :min="0"
              :precision="2"
              placeholder="企业所得税"
            />
            <Button danger type="link" @click="removeLine(idx)">删</Button>
          </Space>
        </div>
        <Button type="dashed" block @click="addLine">加一行</Button>
        <div class="mt-2">行合计（自动）：{{ total.toFixed(2) }}</div>
      </Form.Item>
      <Form.Item label="依据附件" required>
        <FileUpload
          :value="form.evidenceFileUrls || []"
          :max-number="10"
          :max-size="20"
          :multiple="true"
          help-text="税务申报/缴款依据必传"
          @update:value="
            (v) =>
              (form.evidenceFileUrls = Array.isArray(v) ? v : v ? [v] : [])
          "
        />
      </Form.Item>
      <Form.Item label="特殊说明">
        <Input.TextArea v-model:value="form.specialNote" :rows="2" />
      </Form.Item>
    </Form>
  </Modal>
</template>
