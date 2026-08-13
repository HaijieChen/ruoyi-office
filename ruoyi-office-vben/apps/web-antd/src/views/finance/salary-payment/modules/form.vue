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

import { createAndStartSalaryPayment } from '#/api/finance/salary-payment';
import { getSimpleDeptList } from '#/api/system/dept';
import { FileUpload } from '#/components/upload';

defineOptions({ name: 'FinanceSalaryPaymentForm' });

const emit = defineEmits(['success']);

interface Line {
  entityCompanyDeptId?: number;
  netSalaryAmount?: number;
  personalTaxAmount?: number;
  socialInsuranceAmount?: number;
}

const form = ref<{
  paymentTiming?: string;
  periodLabel?: string;
  currency?: string;
  specialNote?: string;
  evidenceFileUrls?: string[];
  lines: Line[];
}>({
  currency: 'CNY',
  paymentTiming: 'IMMEDIATE',
  lines: [{}],
});

const companyOptions = ref<{ label: string; value: number }[]>([]);

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
      Number(l.netSalaryAmount || 0) +
      Number(l.personalTaxAmount || 0) +
      Number(l.socialInsuranceAmount || 0)
    );
  }, 0),
);

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    if (!form.value.periodLabel) {
      message.error('请填写薪资期间');
      return;
    }
    if (!form.value.lines.every((l) => l.entityCompanyDeptId)) {
      message.error('请为每行选择主体公司');
      return;
    }
    modalApi.lock();
    try {
      await createAndStartSalaryPayment({
        paymentTiming: form.value.paymentTiming || 'IMMEDIATE',
        periodLabel: form.value.periodLabel!,
        currency: form.value.currency || 'CNY',
        specialNote: form.value.specialNote,
        evidenceFileUrls: form.value.evidenceFileUrls,
        lines: form.value.lines.map((l) => ({
          entityCompanyDeptId: l.entityCompanyDeptId!,
          netSalaryAmount: Number(l.netSalaryAmount || 0),
          personalTaxAmount: Number(l.personalTaxAmount || 0),
          socialInsuranceAmount: Number(l.socialInsuranceAmount || 0),
        })),
      });
      message.success('已提交薪资付款申请');
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
      lines: [{}],
    };
  },
});
</script>

<template>
  <Modal title="新建薪资付款申请" class="w-[820px]">
    <Form :label-col="{ span: 5 }" :wrapper-col="{ span: 18 }">
      <Form.Item label="薪资期间" required>
        <Input
          v-model:value="form.periodLabel"
          placeholder="如 2026-07"
        />
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
              style="width: 180px"
              :options="companyOptions"
              placeholder="主体公司"
              show-search
              option-filter-prop="label"
            />
            <InputNumber
              v-model:value="line.netSalaryAmount"
              :min="0"
              :precision="2"
              placeholder="实发"
            />
            <InputNumber
              v-model:value="line.personalTaxAmount"
              :min="0"
              :precision="2"
              placeholder="个税"
            />
            <InputNumber
              v-model:value="line.socialInsuranceAmount"
              :min="0"
              :precision="2"
              placeholder="社保"
            />
            <Button danger type="link" @click="removeLine(idx)">删</Button>
          </Space>
        </div>
        <Button type="dashed" block @click="addLine">加一行</Button>
        <div class="mt-2">行合计（自动）：{{ total.toFixed(2) }}</div>
      </Form.Item>
      <Form.Item label="依据附件">
        <FileUpload
          :value="form.evidenceFileUrls || []"
          :max-number="10"
          :max-size="20"
          :multiple="true"
          help-text="员工明细附件受控可选"
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
