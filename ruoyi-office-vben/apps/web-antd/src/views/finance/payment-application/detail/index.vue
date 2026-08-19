<script lang="ts" setup>
/**
 * 付款申请 · BPM 自定义表单「查看」组件（F3 + EXP-87 F1/F6）。
 * processInstance/detail 经 formCustomViewPath 加载，
 * props.id = businessKey（付款申请主键）。
 */
import type { FinancePaymentApplicationApi } from '#/api/finance/payment-application';

import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useTabs } from '@vben/hooks';
import { useUserStore } from '@vben/stores';

import {
  Alert,
  Button,
  Card,
  DatePicker,
  Descriptions,
  Input,
  InputNumber,
  Table,
  message,
  Select,
  Space,
  Spin,
  Tag,
} from 'ant-design-vue';
import dayjs, { type Dayjs } from 'dayjs';

import {
  getPaymentApplication,
  recordPayPaymentApplication,
  updatePaymentAccountingSubject,
} from '#/api/finance/payment-application';
import { getCompanyBankAccountSimpleList } from '#/api/finance/company-bank-account';
import type { DefaultOptionType } from 'ant-design-vue/es/select';

import { getDictOptions } from '@vben/hooks';
import { FileUpload } from '#/components/upload';

defineOptions({ name: 'FinancePaymentApplicationBpmDetail' });

function dictOptions(dictType: string): DefaultOptionType[] {
  return getDictOptions(dictType).map((d) => ({
    label: d.label,
    value: d.value as string | number,
  }));
}

const props = defineProps<{
  activityNodes?: any[];
  id?: number | string;
  isApproval?: boolean;
  nodeKey?: string;
  nodeKeyName?: string;
  processDefinition?: any;
  processInstance?: any;
  taskId?: string;
}>();

const FINANCE_NODE = 'taskFinance';
const CASHIER_NODE = 'taskCashier';
const CASHIER_KEYS = new Set([CASHIER_NODE, 'cashier']);
const FINANCE_KEYS = new Set([FINANCE_NODE, 'finance']);

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const { closeCurrentTab } = useTabs();

const loading = ref(false);
const submitting = ref(false);
const detail = ref<
  (FinancePaymentApplicationApi.Application & {
    applicationKind?: string;
    periodLabel?: string;
    paidLineSum?: number;
    payLines?: any[];
    salaryLines?: any[];
    taxLines?: any[];
  }) | null
>(null);
const accountingSubject = ref('');
const actualPayDate = ref<Dayjs | undefined>(dayjs());
const payVoucherUrl = ref('');
const erpVoucherNo = ref('');
const idempotencyKey = ref('');
function ensureIdempotencyKey() {
  if (!idempotencyKey.value) {
    idempotencyKey.value =
      (globalThis.crypto?.randomUUID?.() as string) ||
      `pay-${Date.now()}-${Math.random().toString(36).slice(2)}`;
  }
  return idempotencyKey.value;
}

const companyBankAccountId = ref<number | undefined>();
const payAmount = ref<number | undefined>();
const payEntityCompanyDeptId = ref<number | undefined>();
const accountOptions = ref<{ label: string; value: number }[]>([]);

function resolveId(): number | undefined {
  if (props.id !== null && props.id !== undefined && props.id !== '') {
    const n = typeof props.id === 'string' ? Number(props.id) : props.id;
    return Number.isFinite(n) ? n : undefined;
  }
  const q = route.query.id;
  if (q !== null && q !== undefined && q !== '') {
    const n = Number(Array.isArray(q) ? q[0] : q);
    return Number.isFinite(n) ? n : undefined;
  }
  return undefined;
}

function resolveNodeKey(): string {
  if (props.nodeKey) return props.nodeKey;
  const q = route.query.nodeKey;
  if (q !== null && q !== undefined && q !== '') {
    return String(Array.isArray(q) ? q[0] : q);
  }
  return detail.value?.currentNodeKey || '';
}

function resolveTaskId(): string {
  if (props.taskId) return String(props.taskId);
  const q = route.query.taskId;
  if (q !== null && q !== undefined && q !== '') {
    return String(Array.isArray(q) ? q[0] : q);
  }
  return '';
}

const resolvedNodeKey = computed(() => resolveNodeKey());
const resolvedTaskId = computed(() => resolveTaskId());
const isFinanceNode = computed(() => FINANCE_KEYS.has(resolvedNodeKey.value));
const isCashierNode = computed(() => CASHIER_KEYS.has(resolvedNodeKey.value));

const canResubmit = computed(() => {
  const d = detail.value;
  if (!d || d.status !== 'REJECTED') return false;
  const uid = userStore.userInfo?.id;
  return uid != null && Number(d.applicantUserId) === Number(uid);
});

const remainingPay = computed(() => {
  const apply = Number(detail.value?.applyAmount || 0);
  const paid = Number(detail.value?.paidLineSum || 0);
  return Math.max(0, +(apply - paid).toFixed(2));
});

/** F6：可选支付主体（薪资/税金多主体；普通付款仅单头） */
const payEntityOptions = computed(() => {
  const d = detail.value;
  if (!d) return [] as { label: string; value: number }[];
  const kind = d.applicationKind || 'ORDINARY';
  if (kind === 'SALARY' && d.salaryLines?.length) {
    const map = new Map<number, string>();
    for (const l of d.salaryLines) {
      if (l.entityCompanyDeptId != null) {
        map.set(l.entityCompanyDeptId, l.entityCompanyName || String(l.entityCompanyDeptId));
      }
    }
    return [...map.entries()].map(([value, label]) => ({ value, label }));
  }
  if (kind === 'TAX' && d.taxLines?.length) {
    const map = new Map<number, string>();
    for (const l of d.taxLines) {
      if (l.entityCompanyDeptId != null) {
        map.set(l.entityCompanyDeptId, l.entityCompanyName || String(l.entityCompanyDeptId));
      }
    }
    return [...map.entries()].map(([value, label]) => ({ value, label }));
  }
  if (d.entityCompanyDeptId != null) {
    return [
      {
        value: d.entityCompanyDeptId,
        label: d.entityCompanyName || String(d.entityCompanyDeptId),
      },
    ];
  }
  return [];
});

const multiEntity = computed(() => payEntityOptions.value.length > 1);

async function loadAccounts(entityCompanyDeptId?: number) {
  accountOptions.value = [];
  companyBankAccountId.value = undefined;
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

async function loadData() {
  const id = resolveId();
  if (id === undefined) {
    detail.value = null;
    return;
  }
  loading.value = true;
  try {
    detail.value = await getPaymentApplication(id);
    accountingSubject.value = detail.value?.accountingSubject || '';
    payAmount.value = remainingPay.value || Number(detail.value?.applyAmount || 0);
    ensureIdempotencyKey();
    const entities = payEntityOptions.value;
    payEntityCompanyDeptId.value =
      entities[0]?.value ?? detail.value?.entityCompanyDeptId;
    await loadAccounts(payEntityCompanyDeptId.value);
  } catch (error) {
    detail.value = null;
    message.error(error instanceof Error ? error.message : '加载付款详情失败');
  } finally {
    loading.value = false;
  }
}

async function handleSaveSubject() {
  const id = resolveId();
  const tid = resolvedTaskId.value;
  if (id === undefined || !tid) {
    message.error('缺少申请或任务编号，请从待办进入');
    return;
  }
  if (!accountingSubject.value?.trim()) {
    message.warning('请选择费用科目/性质');
    return;
  }
  submitting.value = true;
  try {
    await updatePaymentAccountingSubject(id, tid, accountingSubject.value.trim());
    message.success('费用科目/性质已保存，可点击底部「通过」完成财务节点');
    await loadData();
  } catch (error) {
    message.error(error instanceof Error ? error.message : '保存失败');
  } finally {
    submitting.value = false;
  }
}

async function handleRecordPay() {
  const id = resolveId();
  const tid = resolvedTaskId.value;
  if (id === undefined || !tid) {
    message.error('缺少申请或任务编号，请从待办进入');
    return;
  }
  if (!companyBankAccountId.value) {
    message.warning('请选择付款账户');
    return;
  }
  if (!actualPayDate.value || !payVoucherUrl.value?.trim()) {
    message.warning('支付日与支付凭证必填');
    return;
  }
  if (!payAmount.value || payAmount.value <= 0) {
    message.warning('本笔支付金额须大于 0');
    return;
  }
  submitting.value = true;
  try {
    await recordPayPaymentApplication({
      id,
      taskId: tid,
      companyBankAccountId: companyBankAccountId.value,
      payAmount: payAmount.value,
      actualPayDate: actualPayDate.value.format('YYYY-MM-DD'),
      payVoucherUrl: payVoucherUrl.value.trim(),
      erpVoucherNo: erpVoucherNo.value || undefined,
      idempotencyKey: ensureIdempotencyKey(),
    });
    message.success(
      remainingPay.value - Number(payAmount.value) > 0.001
        ? '本笔支付已登记（尚未足额，可继续登记）'
        : '出纳办结成功',
    );
    idempotencyKey.value = '';
    await loadData();
  } catch (error) {
    message.error(error instanceof Error ? error.message : '出纳办结失败');
  } finally {
    submitting.value = false;
  }
}

async function handleGoResubmit() {
  const id = resolveId() ?? detail.value?.id;
  if (id === undefined) return;
  try {
    await closeCurrentTab();
  } catch {
    // ignore
  }
  await router.push({
    path: '/finance/payment-application',
    query: { openResubmit: String(id) },
  });
}

watch(payEntityCompanyDeptId, (v) => {
  void loadAccounts(v);
});

onMounted(() => {
  void loadData();
});
watch(
  () => props.id,
  () => {
    void loadData();
  },
);
</script>

<template>
  <div class="payment-application-bpm-detail p-4">
    <Spin :spinning="loading">
      <template v-if="detail">
        <div class="mb-4 flex flex-wrap items-center gap-2">
          <span class="text-base font-medium">付款申请</span>
          <Tag color="blue">{{ detail.applicationNo || '-' }}</Tag>
          <Tag>{{ detail.status }}</Tag>
          <Tag v-if="isFinanceNode || isCashierNode" color="orange">
            {{ props.nodeKeyName || resolvedNodeKey }}
          </Tag>
        </div>

        <Alert
          v-if="canResubmit"
          class="mb-4"
          type="warning"
          show-icon
          message="本单已驳回"
          description="请修改后重提，将整链重批。"
        >
          <template #action>
            <Button type="primary" @click="handleGoResubmit">修改并重提</Button>
          </template>
        </Alert>

        <Card
          v-if="isFinanceNode && isApproval !== false"
          class="mb-4"
          size="small"
          title="财务审核 · 费用科目/性质"
        >
          <Alert
            class="mb-3"
            type="info"
            show-icon
            message="请先保存费用科目/性质，再使用底部「通过」。未填写无法 complete。"
          />
          <div class="mb-3 flex flex-wrap items-center gap-2">
            <Select
              v-model:value="accountingSubject"
              class="min-w-[220px]"
              :options="dictOptions('finance_accounting_subject')"
              placeholder="请选择费用科目/性质"
            />
            <Button
              type="primary"
              :loading="submitting"
              :disabled="!resolvedTaskId || !accountingSubject"
              @click="handleSaveSubject"
            >
              保存费用科目/性质
            </Button>
          </div>
        </Card>

        <!-- F1/F6 出纳：账户必选 + 金额/多笔 residual -->
        <Card
          v-if="isCashierNode && isApproval !== false"
          class="mb-4"
          size="small"
          title="出纳支付办结"
        >
          <Alert
            class="mb-3"
            type="info"
            show-icon
            :message="`申请金额 ${detail.applyAmount} ${detail.currency || ''}，已登记 ${detail.paidLineSum ?? 0}，剩余 ${remainingPay}。请勿使用底部通用「通过」。`"
          />
          <div v-if="multiEntity" class="mb-2">
            <div class="mb-1 text-sm text-gray-600">付款主体公司</div>
            <Select
              v-model:value="payEntityCompanyDeptId"
              class="w-full max-w-md"
              :options="payEntityOptions"
              placeholder="按明细主体选择"
            />
          </div>
          <div class="mb-2">
            <div class="mb-1 text-sm text-gray-600">付款账户（必选）</div>
            <Select
              v-model:value="companyBankAccountId"
              class="w-full max-w-md"
              :options="accountOptions"
              show-search
              option-filter-prop="label"
              placeholder="仅显示该主体启用账户（账号脱敏）"
            />
          </div>
          <div class="mb-2">
            <div class="mb-1 text-sm text-gray-600">本笔支付金额</div>
            <InputNumber
              v-model:value="payAmount"
              class="w-full max-w-xs"
              :min="0.01"
              :max="remainingPay || undefined"
              :precision="2"
            />
          </div>
          <div class="mb-2">
            <div class="mb-1 text-sm text-gray-600">实际支付日期</div>
            <DatePicker v-model:value="actualPayDate" class="w-full max-w-xs" />
          </div>
          <div class="mb-2">
            <div class="mb-1 text-sm text-gray-600">支付凭证</div>
            <FileUpload
              :value="payVoucherUrl ? [payVoucherUrl] : []"
              :max-number="1"
              :max-size="20"
              :multiple="false"
              help-text="上传回单/截图"
              @update:value="
                (v: string | string[]) => {
                  const arr = Array.isArray(v) ? v : v ? [v] : [];
                  payVoucherUrl = arr[0] || '';
                }
              "
            />
          </div>
          <div class="mb-3">
            <div class="mb-1 text-sm text-gray-600">ERP 凭证号（选填）</div>
            <Input v-model:value="erpVoucherNo" class="max-w-md" />
          </div>
          <Space>
            <Button
              type="primary"
              :loading="submitting"
              :disabled="!resolvedTaskId || remainingPay <= 0"
              @click="handleRecordPay"
            >
              {{ remainingPay <= 0 ? '已足额' : '提交本笔支付' }}
            </Button>
          </Space>
          <div v-if="!resolvedTaskId" class="mt-2 text-sm text-orange-600">
            未拿到 taskId，请从「我的待办」进入
          </div>
        </Card>

        <Descriptions bordered :column="2" size="small" class="mb-4">
          <Descriptions.Item label="单号">{{ detail.applicationNo }}</Descriptions.Item>
          <Descriptions.Item label="状态">{{ detail.status }}</Descriptions.Item>
          <Descriptions.Item label="主体公司">
            {{ detail.entityCompanyName || '历史未记录' }}
          </Descriptions.Item>
          <Descriptions.Item label="收款方">{{ detail.payeeName || '-' }}</Descriptions.Item>
          <Descriptions.Item label="金额">
            {{ detail.applyAmount }} {{ detail.currency }}
          </Descriptions.Item>
          <Descriptions.Item
            v-if="!detail.applicationKind || detail.applicationKind === 'ORDINARY'"
            label="产品名称"
          >
            {{ detail.costProject || '-' }}
          </Descriptions.Item>
          <Descriptions.Item label="事由">{{ detail.paymentReason }}</Descriptions.Item>
          <Descriptions.Item label="时效">{{ detail.paymentTiming }}</Descriptions.Item>
          <Descriptions.Item label="期间">{{ detail.periodLabel || '-' }}</Descriptions.Item>
          <Descriptions.Item label="账户" :span="2">
            {{ detail.payeeBankName || '-' }} / {{ detail.payeeBankAccount || '-' }}
          </Descriptions.Item>
          <Descriptions.Item label="已登记支付合计">
            {{ detail.paidLineSum ?? 0 }}
          </Descriptions.Item>
          <Descriptions.Item label="支付日">{{ detail.actualPayDate || '-' }}</Descriptions.Item>
          <Descriptions.Item label="依据" :span="2">
            {{ detail.evidenceFileUrls }}
          </Descriptions.Item>
        </Descriptions>

        <Card
          v-if="detail.payLines?.length"
          class="mb-4"
          size="small"
          title="支付明细"
        >
          <Table
            size="small"
            :pagination="false"
            row-key="id"
            :data-source="detail.payLines"
            :columns="[
              { title: '账户', dataIndex: 'accountNameSnapshot', key: 'a' },
              { title: '开户行', dataIndex: 'bankNameSnapshot', key: 'b' },
              { title: '账号', dataIndex: 'accountNoMaskedSnapshot', key: 'c' },
              { title: '金额', dataIndex: 'payAmount', key: 'd' },
              { title: '支付日', dataIndex: 'actualPayDate', key: 'e' },
            ]"
          />
        </Card>

        <Card
          v-if="detail.salaryLines?.length"
          class="mb-4"
          size="small"
          title="薪资多主体明细"
        >
          <Table
            size="small"
            :pagination="false"
            row-key="id"
            :data-source="detail.salaryLines"
            :columns="[
              { title: '主体公司', dataIndex: 'entityCompanyName', key: 'a' },
              { title: '实发', dataIndex: 'netSalaryAmount', key: 'b' },
              { title: '个税', dataIndex: 'personalTaxAmount', key: 'c' },
              { title: '社保', dataIndex: 'socialInsuranceAmount', key: 'd' },
              { title: '行合计', dataIndex: 'lineTotal', key: 'e' },
            ]"
          />
        </Card>

        <Card
          v-if="detail.taxLines?.length"
          class="mb-4"
          size="small"
          title="税金多主体明细"
        >
          <Table
            size="small"
            :pagination="false"
            row-key="id"
            :data-source="detail.taxLines"
            :columns="[
              { title: '主体公司', dataIndex: 'entityCompanyName', key: 'a' },
              { title: '增值税', dataIndex: 'vatAmount', key: 'b' },
              { title: '附加税', dataIndex: 'surchargeAmount', key: 'c' },
              { title: '印花税', dataIndex: 'stampTaxAmount', key: 'd' },
              { title: '企业所得税', dataIndex: 'citAmount', key: 'e' },
              { title: '行合计', dataIndex: 'lineTotal', key: 'f' },
            ]"
          />
        </Card>
      </template>
      <div v-else-if="!loading" class="text-gray-500">
        无法加载详情。请确认 businessKey 与 formCustomViewPath 配置。
      </div>
    </Spin>
  </div>
</template>
