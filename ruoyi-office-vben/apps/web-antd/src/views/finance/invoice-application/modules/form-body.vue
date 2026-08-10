<script lang="ts" setup>
import type { Rule } from 'ant-design-vue/es/form';

import type { FinanceCustomerCompanyApi } from '#/api/finance/customer-company';

import { computed, ref } from 'vue';

import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';

import {
  Button,
  Form,
  Input,
  InputNumber,
  message,
  Select,
  Space,
  Textarea,
} from 'ant-design-vue';

import {
  getBusinessOrder,
  getBusinessOrderPage,
} from '#/api/finance/business-order';
import { getCustomerCompanySimpleList } from '#/api/finance/customer-company';
import {
  createAndStartInvoiceApplication,
  getInvoiceApplication,
  resubmitInvoiceApplication,
} from '#/api/finance/invoice-application';
import { getSimpleCompanyList } from '#/api/system/dept';

defineOptions({ name: 'FinanceInvoiceApplicationFormBody' });

const emit = defineEmits<{
  predictChange: [vars: Record<string, unknown>];
  success: [];
}>();
const submitting = ref(false);

interface LineItem {
  businessOrderId?: number;
  amount?: number;
  billingPeriod?: string;
}

interface FormData {
  id?: number;
  mode?: 'create' | 'resubmit';
  customerCompanyId?: number;
  buyerName?: string;
  buyerTaxNo?: string;
  buyerAddressPhone?: string;
  buyerBankAccount?: string;
  /** 组织公司 deptId */
  invoiceCompanyDeptId?: number;
  /** 公司名称快照 */
  invoiceCompany?: string;
  invoiceType?: string;
  /** 产品类型（服务端字段 taxContent） */
  taxContent?: string;
  /** 特别开票要求（单据级，不进客户档案） */
  specialInvoiceRequirement?: string;
  remark?: string;
  lines: LineItem[];
}

interface CompanyOption {
  label: string;
  value: number;
}

interface CustomerCompanyOption {
  label: string;
  value: number;
  taxNo?: string;
  addressPhone?: string;
  bankAccount?: string;
}

interface BoOption {
  label: string;
  value: number;
  /** 可开余额 = settlement - invoiced_occupied */
  invoiceOpenableAmount?: number;
  settlementAmount?: number;
  orderNo?: string;
}

const formRef = ref();
const formData = ref<FormData>({ lines: [{}] });
const boOptions = ref<BoOption[]>([]);
const companyOptions = ref<CompanyOption[]>([]);
const customerCompanyOptions = ref<CustomerCompanyOption[]>([]);
const loadingBo = ref(false);
const loadingCompany = ref(false);
const loadingCustomerCompany = ref(false);
/** resubmit 时原客户公司已停用：保留上次快照只读展示，须重选启用档 */
const priorBuyerSnapshotHint = ref<string | undefined>();
/** orderNo 远程搜索防抖 */
let boSearchTimer: ReturnType<typeof setTimeout> | undefined;

const isResubmit = computed(() => formData.value.mode === 'resubmit');

const invoiceTypeOptions = computed(() =>
  getDictOptions(DICT_TYPE.FINANCE_INVOICE_TYPE).map((d) => ({
    label: d.label,
    value: d.value as number | string,
  })),
);

const productTypeOptions = computed(() =>
  getDictOptions(DICT_TYPE.FINANCE_PRODUCT_TYPE).map((d) => ({
    label: d.label,
    value: d.value as number | string,
  })),
);

function openableOf(bo: {
  invoicedOccupiedAmount?: number;
  invoiceOpenableAmount?: number;
  remainingBalance?: number;
  settlementAmount?: number;
}): number {
  if (
    bo.invoiceOpenableAmount !== null &&
    bo.invoiceOpenableAmount !== undefined &&
    !Number.isNaN(Number(bo.invoiceOpenableAmount))
  ) {
    return Number(bo.invoiceOpenableAmount);
  }
  // 兼容旧后端：无 invoiceOpenableAmount 时回退结算（不误用认领 remainingBalance）
  return Number(bo.settlementAmount ?? 0);
}

/**
 * 明细行金额上限（绑定 InputNumber max / 校验）。
 * 关键：可开未知时返回 undefined，**绝不返回 0**——
 * max=0 且 min=0.01 会形成非法区间，Chrome a11y 显示 valuemax=0，步进/提交易异常。
 */
function lineAmountMax(line?: LineItem): number | undefined {
  if (!line?.businessOrderId) {
    return undefined;
  }
  const opt = boOptions.value.find((o) => o.value === line.businessOrderId);
  if (!opt) {
    return undefined;
  }
  // 仅当选项明确带了可开数字时才限制；缺失则不设 max（避免假 0）
  if (
    opt.invoiceOpenableAmount === null ||
    opt.invoiceOpenableAmount === undefined ||
    Number.isNaN(Number(opt.invoiceOpenableAmount))
  ) {
    return undefined;
  }
  const openable = Number(opt.invoiceOpenableAmount);
  if (!Number.isFinite(openable) || openable <= 0) {
    return undefined;
  }
  return Number(openable.toFixed(2));
}

/** 行可开余额：有有效数字返回 number；选项缺失或未知返回 undefined（勿用 ?? 0 抹平） */
function lineOpenableAmount(line?: LineItem): number | undefined {
  if (!line?.businessOrderId) {
    return undefined;
  }
  const opt = boOptions.value.find((o) => o.value === line.businessOrderId);
  if (
    !opt ||
    opt.invoiceOpenableAmount === null ||
    opt.invoiceOpenableAmount === undefined
  ) {
    return undefined;
  }
  const n = Number(opt.invoiceOpenableAmount);
  return Number.isFinite(n) ? n : undefined;
}

const rules: Record<string, Rule[]> = {
  customerCompanyId: [
    { required: true, message: '请选择客户公司', trigger: 'change' },
  ],
  invoiceCompanyDeptId: [
    { required: true, message: '请选择开票公司', trigger: 'change' },
  ],
  invoiceType: [
    { required: true, message: '请选择发票类型', trigger: 'change' },
  ],
  lines: [
    {
      validator: async () => {
        const lines = formData.value.lines || [];
        if (lines.length === 0) throw new Error('请至少添加一行明细');
        // 同商务单多行合计不得超可开
        const sumByBo = new Map<number, number>();
        for (const [idx, line] of lines.entries()) {
          if (!line.businessOrderId) {
            throw new Error(`第 ${idx + 1} 行：请选择商务单`);
          }
          if (!line.amount || line.amount <= 0) {
            throw new Error(`第 ${idx + 1} 行：金额须大于 0`);
          }
          const openable = lineOpenableAmount(line);
          // 明确可开为 0 才拦；未知（undefined）不在此当成 0 误杀（重提兜底项无 openable 时）
          if (openable !== null && openable !== undefined && openable <= 0) {
            throw new Error(`第 ${idx + 1} 行：该商务单无可开余额，请更换`);
          }
          if (
            openable !== null &&
            openable !== undefined &&
            line.amount > openable + 1e-9
          ) {
            throw new Error(
              `第 ${idx + 1} 行：开票金额不可超过可开余额 ¥${openable.toFixed(2)}`,
            );
          }
          const prev = sumByBo.get(line.businessOrderId) || 0;
          sumByBo.set(line.businessOrderId, prev + Number(line.amount));
        }
        for (const [boId, total] of sumByBo.entries()) {
          const opt = boOptions.value.find((o) => o.value === boId);
          if (
            !opt ||
            opt.invoiceOpenableAmount === null ||
            opt.invoiceOpenableAmount === undefined
          ) {
            continue;
          }
          const openable = Number(opt.invoiceOpenableAmount);
          if (!Number.isFinite(openable)) continue;
          if (total > openable + 1e-9) {
            const label = opt.orderNo || `#${boId}`;
            throw new Error(
              `商务单 ${label}：多行合计 ¥${total.toFixed(2)} 超过可开余额 ¥${openable.toFixed(2)}`,
            );
          }
        }
      },
    },
  ],
};

function formatBoLabel(bo: {
  id: number;
  invoiceOpenableAmount?: number;
  orderNo?: string;
  payerName?: string;
  productName?: string;
  settlementAmount?: number;
}) {
  const openable = openableOf(bo);
  return `${bo.orderNo || bo.id} | ${bo.productName || '-'} | ${bo.payerName || '-'} | 可开 ¥${openable.toFixed(2)}`;
}

function mapBoOption(bo: any): BoOption {
  const invoiceOpenableAmount = openableOf(bo);
  return {
    value: bo.id as number,
    orderNo: bo.orderNo as string,
    invoiceOpenableAmount,
    settlementAmount: Number(bo.settlementAmount ?? 0),
    label: formatBoLabel(bo),
  };
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
      onlyOpenable: true,
    });
    const mapped = (page?.list || []).map((bo: any) => mapBoOption(bo));
    // 保留已选但不在当前页的选项，避免重提/筛选后丢 label
    const selectedIds = new Set(
      (formData.value.lines || [])
        .map((l) => l.businessOrderId)
        .filter((id): id is number => !!id),
    );
    const keep = boOptions.value.filter(
      (o) =>
        selectedIds.has(o.value) && !mapped.some((m) => m.value === o.value),
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

/** 选中商务单：回填建议金额 = 可开余额 */
async function onBoChange(index: number, boId?: number) {
  if (!boId) return;
  const line = formData.value.lines[index];
  if (!line) return;
  let opt = boOptions.value.find((o) => o.value === boId);
  if (!opt) {
    try {
      const bo = await getBusinessOrder(boId);
      opt = mapBoOption(bo);
      upsertBoOption(opt);
    } catch {
      return;
    }
  }
  if (line.amount === null || line.amount === undefined || line.amount <= 0) {
    const suggest = Number(opt.invoiceOpenableAmount) || 0;
    if (suggest > 0) {
      line.amount = Number(suggest.toFixed(2));
    }
  }
}

/** 重提时：补全当前行已选商务单的下拉项（即使可开=0 也保留，便于用户改金额或换单） */
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
        upsertBoOption(mapBoOption(bo));
      } catch {
        // 不要写 openable=0：会被校验当成「无可开余额」，且 InputNumber max=0 与 min=0.01 冲突
        upsertBoOption({
          value: id,
          label: `商务单 #${id}`,
          invoiceOpenableAmount: undefined,
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

async function loadCompanyOptions() {
  loadingCompany.value = true;
  try {
    const list = (await getSimpleCompanyList()) || [];
    companyOptions.value = list
      .filter((c) => c.id !== null && c.id !== undefined)
      .map((c) => ({
        value: c.id as number,
        label: c.name,
      }));
  } finally {
    loadingCompany.value = false;
  }
}

/** 选中开票主体公司：同步名称快照 */
function onCompanyChange(deptId?: number) {
  if (deptId === null || deptId === undefined) {
    formData.value.invoiceCompany = undefined;
    return;
  }
  const opt = companyOptions.value.find((o) => o.value === deptId);
  formData.value.invoiceCompany = opt?.label;
}

function joinNonEmpty(...parts: Array<null | string | undefined>): string {
  return parts
    .map((p) => (p === null || p === undefined ? '' : String(p).trim()))
    .filter(Boolean)
    .join(' ');
}

function mapCustomerCompanyOption(
  c: FinanceCustomerCompanyApi.CustomerCompany,
): CustomerCompanyOption {
  return {
    value: c.id,
    label: c.name,
    taxNo: c.taxNo,
    addressPhone: joinNonEmpty(c.address, c.phone),
    bankAccount: joinNonEmpty(c.bankName, c.bankAccount),
  };
}

async function loadCustomerCompanyOptions() {
  loadingCustomerCompany.value = true;
  try {
    const list = await getCustomerCompanySimpleList();
    customerCompanyOptions.value = (list || []).map((c) =>
      mapCustomerCompanyOption(c),
    );
  } finally {
    loadingCustomerCompany.value = false;
  }
}

/** 选中购方客户公司：只读税项 */
function onCustomerCompanyChange(id?: number) {
  if (id === null || id === undefined) {
    formData.value.buyerName = undefined;
    formData.value.buyerTaxNo = undefined;
    formData.value.buyerAddressPhone = undefined;
    formData.value.buyerBankAccount = undefined;
    return;
  }
  const opt = customerCompanyOptions.value.find((o) => o.value === id);
  if (!opt) return;
  priorBuyerSnapshotHint.value = undefined;
  formData.value.buyerName = opt.label;
  formData.value.buyerTaxNo = opt.taxNo;
  formData.value.buyerAddressPhone = opt.addressPhone;
  formData.value.buyerBankAccount = opt.bankAccount;
}

function getPredictVariables(): Record<string, unknown> {
  return {};
}

async function reset(opts?: { id?: number; mode?: string }) {
  await Promise.all([
    loadBusinessOrderOptions(),
    loadCompanyOptions(),
    loadCustomerCompanyOptions(),
  ]);
  if (opts?.id && opts?.mode === 'resubmit') {
    const detail = await getInvoiceApplication(opts.id);
    formData.value = {
      id: detail.id,
      mode: 'resubmit',
      customerCompanyId: detail.customerCompanyId,
      buyerName: detail.buyerName,
      buyerTaxNo: detail.buyerTaxNo,
      buyerAddressPhone: detail.buyerAddressPhone,
      buyerBankAccount: detail.buyerBankAccount,
      invoiceCompanyDeptId: detail.invoiceCompanyDeptId,
      invoiceCompany: detail.invoiceCompany,
      invoiceType: detail.invoiceType,
      taxContent: detail.taxContent,
      specialInvoiceRequirement: detail.specialInvoiceRequirement,
      remark: detail.remark as any,
      lines: (detail.lines || []).map((l) => ({
        businessOrderId: l.businessOrderId,
        amount: Number(l.amount),
        billingPeriod: l.billingPeriod,
      })),
    };
    priorBuyerSnapshotHint.value = undefined;
    if (
      formData.value.customerCompanyId !== null &&
      formData.value.customerCompanyId !== undefined
    ) {
      const stillEnabled = customerCompanyOptions.value.some(
        (o) => o.value === formData.value.customerCompanyId,
      );
      if (stillEnabled) {
        onCustomerCompanyChange(formData.value.customerCompanyId);
      } else {
        const snapName =
          formData.value.buyerName || `#${formData.value.customerCompanyId}`;
        priorBuyerSnapshotHint.value = `原客户公司「${snapName}」已停用或不存在，请重新选择启用中的客户公司`;
        formData.value.customerCompanyId = undefined;
        formData.value.buyerName = undefined;
        formData.value.buyerTaxNo = undefined;
        formData.value.buyerAddressPhone = undefined;
        formData.value.buyerBankAccount = undefined;
        message.warning(priorBuyerSnapshotHint.value);
      }
    }
    if (
      formData.value.invoiceCompanyDeptId === null ||
      (formData.value.invoiceCompanyDeptId === undefined &&
        formData.value.invoiceCompany)
    ) {
      formData.value.invoiceCompanyDeptId = undefined;
    } else if (
      formData.value.invoiceCompanyDeptId !== null &&
      formData.value.invoiceCompanyDeptId !== undefined &&
      !companyOptions.value.some(
        (o) => o.value === formData.value.invoiceCompanyDeptId,
      )
    ) {
      companyOptions.value = [
        {
          value: formData.value.invoiceCompanyDeptId,
          label:
            formData.value.invoiceCompany ||
            `公司 #${formData.value.invoiceCompanyDeptId}`,
        },
        ...companyOptions.value,
      ];
    }
    if (formData.value.lines.length === 0) {
      formData.value.lines = [{}];
    }
    await ensureSelectedBoOptions(formData.value.lines);
  } else {
    priorBuyerSnapshotHint.value = undefined;
    formData.value = { mode: 'create', lines: [{}] };
  }
  emit('predictChange', getPredictVariables());
}

interface SubmitContext {
  startUserSelectAssignees?: Record<string, number[]>;
}

async function submit(ctx?: SubmitContext): Promise<void> {
  await formRef.value?.validate();
  if (!formData.value.customerCompanyId) {
    message.warning(priorBuyerSnapshotHint.value || '请选择启用中的客户公司');
    throw new Error('validation');
  }
  if (
    !customerCompanyOptions.value.some(
      (o) => o.value === formData.value.customerCompanyId,
    )
  ) {
    message.warning('所选客户公司不可用，请从启用列表中重新选择');
    formData.value.customerCompanyId = undefined;
    throw new Error('validation');
  }
  if (!formData.value.invoiceCompanyDeptId) {
    message.warning('请选择开票公司');
    throw new Error('validation');
  }
  onCompanyChange(formData.value.invoiceCompanyDeptId);
  onCustomerCompanyChange(formData.value.customerCompanyId);
  submitting.value = true;
  try {
    const payload = {
      customerCompanyId: formData.value.customerCompanyId as number,
      buyerName: formData.value.buyerName,
      buyerTaxNo: formData.value.buyerTaxNo,
      buyerAddressPhone: formData.value.buyerAddressPhone,
      buyerBankAccount: formData.value.buyerBankAccount,
      invoiceCompanyDeptId: formData.value.invoiceCompanyDeptId,
      invoiceCompany: formData.value.invoiceCompany,
      invoiceType: formData.value.invoiceType,
      taxContent: formData.value.taxContent,
      specialInvoiceRequirement: formData.value.specialInvoiceRequirement,
      remark: formData.value.remark,
      lines: formData.value.lines.map((l) => ({
        businessOrderId: l.businessOrderId as number,
        amount: l.amount as number,
        billingPeriod: l.billingPeriod,
        invoiceCompany: formData.value.invoiceCompany,
        invoiceType: formData.value.invoiceType,
      })),
      startUserSelectAssignees: ctx?.startUserSelectAssignees,
    };
    if (isResubmit.value && formData.value.id) {
      await resubmitInvoiceApplication(formData.value.id, {
        ...payload,
        id: formData.value.id,
      });
      message.success('已重提并启动新审批');
    } else {
      await createAndStartInvoiceApplication(payload);
      message.success('已提交并启动审批（无草稿）');
    }
    emit('success');
  } finally {
    submitting.value = false;
  }
}

defineExpose({ reset, submit, getPredictVariables, submitting });
</script>

<template>
  <div>
    <div class="mb-3 rounded bg-blue-50 px-3 py-2 text-sm text-blue-800">
      仅支持「提交即启流」。无草稿。购方须从客户公司档案选择，税项只读（服务端以档案快照为准）。商务单仅可开余额
      &gt; 0。
    </div>
    <div
      v-if="priorBuyerSnapshotHint"
      class="mb-3 rounded bg-amber-50 px-3 py-2 text-sm text-amber-900"
    >
      {{ priorBuyerSnapshotHint }}
    </div>
    <Form
      ref="formRef"
      :model="formData"
      :rules="rules"
      :label-col="{ span: 5 }"
      :wrapper-col="{ span: 18 }"
    >
      <Form.Item label="客户公司" name="customerCompanyId" required>
        <Select
          v-model:value="formData.customerCompanyId"
          class="w-full"
          show-search
          allow-clear
          :loading="loadingCustomerCompany"
          :options="customerCompanyOptions"
          option-filter-prop="label"
          placeholder="从启用中的客户公司选择"
          @change="(v: any) => onCustomerCompanyChange(v)"
          @dropdown-visible-change="
            (open: boolean) => {
              if (open) loadCustomerCompanyOptions();
            }
          "
        />
      </Form.Item>
      <Form.Item label="购方名称">
        <Input
          :value="formData.buyerName"
          disabled
          placeholder="选档后自动带出"
        />
      </Form.Item>
      <Form.Item label="购方税号">
        <Input :value="formData.buyerTaxNo" disabled />
      </Form.Item>
      <Form.Item label="地址、电话">
        <Input :value="formData.buyerAddressPhone" disabled />
      </Form.Item>
      <Form.Item label="开户行及账号">
        <Input :value="formData.buyerBankAccount" disabled />
      </Form.Item>
      <Form.Item label="特别开票要求" name="specialInvoiceRequirement">
        <Textarea
          v-model:value="formData.specialInvoiceRequirement"
          :rows="2"
          placeholder="客户当次要求（可选，不进客户档案）"
          allow-clear
        />
      </Form.Item>
      <Form.Item label="开票公司" name="invoiceCompanyDeptId" required>
        <Select
          v-model:value="formData.invoiceCompanyDeptId"
          class="w-full"
          show-search
          allow-clear
          :loading="loadingCompany"
          :options="companyOptions"
          option-filter-prop="label"
          placeholder="请选择组织架构中的公司"
          @change="(v: any) => onCompanyChange(v)"
          @dropdown-visible-change="
            (open: boolean) => {
              if (open && companyOptions.length === 0) loadCompanyOptions();
            }
          "
        />
      </Form.Item>
      <Form.Item label="发票类型" name="invoiceType" required>
        <Select
          v-model:value="formData.invoiceType"
          class="w-full"
          allow-clear
          :options="invoiceTypeOptions"
          placeholder="请选择发票类型"
        />
      </Form.Item>
      <Form.Item label="产品类型" name="taxContent">
        <Select
          v-model:value="formData.taxContent"
          class="w-full"
          allow-clear
          :options="productTypeOptions"
          placeholder="请选择产品类型"
        />
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
              <Button
                type="link"
                danger
                size="small"
                @click="removeLine(index)"
              >
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
                  placeholder="搜索单号（仅可开&gt;0）"
                  :filter-option="false"
                  @search="onBoSearch"
                  @change="(v: any) => onBoChange(index, v)"
                  @dropdown-visible-change="
                    (open: boolean) => {
                      if (open && boOptions.length === 0)
                        loadBusinessOrderOptions();
                    }
                  "
                />
              </div>
              <div>
                <div class="mb-1 text-xs text-gray-500">开票金额</div>
                <InputNumber
                  v-model:value="line.amount"
                  class="w-full"
                  placeholder="不超过可开"
                  :min="0.01"
                  :max="lineAmountMax(line)"
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
  </div>
</template>
