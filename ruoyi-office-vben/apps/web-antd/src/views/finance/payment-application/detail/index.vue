<script lang="ts" setup>
/**
 * 付款申请 · BPM 自定义表单「查看」组件（F3）。
 * processInstance/detail 经 formCustomViewPath 加载，
 * props.id = businessKey（付款申请主键）。
 * 财务节点：填写费用科目/性质；出纳节点：支付登记（勿用通用通过）。
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
// PShell 可能传 product key「cashier」
const CASHIER_KEYS = new Set([CASHIER_NODE, 'cashier']);
const FINANCE_KEYS = new Set([FINANCE_NODE, 'finance']);

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const { closeCurrentTab } = useTabs();

const loading = ref(false);
const submitting = ref(false);
const detail = ref<FinancePaymentApplicationApi.Application | null>(null);
const accountingSubject = ref('');
const actualPayDate = ref<Dayjs | undefined>(dayjs());
const payVoucherUrl = ref('');
const erpVoucherNo = ref('');

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
  // 从台账 currentNodeKey 兜底
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
  if (!actualPayDate.value || !payVoucherUrl.value?.trim()) {
    message.warning('支付日与支付凭证必填');
    return;
  }
  submitting.value = true;
  try {
    await recordPayPaymentApplication({
      id,
      taskId: tid,
      actualPayDate: actualPayDate.value.format('YYYY-MM-DD'),
      payVoucherUrl: payVoucherUrl.value.trim(),
      erpVoucherNo: erpVoucherNo.value || undefined,
    });
    message.success('出纳办结成功');
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

        <!-- F4 财务节点：费用科目/性质，仅财务审批节点可见 -->
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
          <div v-if="!resolvedTaskId" class="text-sm text-orange-600">
            未拿到 taskId，请从「我的待办」进入
          </div>
        </Card>

        <!-- F3 出纳节点 -->
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
            message="请填写支付日与凭证后提交。请勿使用底部通用「通过」。"
          />
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
            <Input v-model:value="erpVoucherNo" />
          </div>
          <Space>
            <Button
              type="primary"
              :loading="submitting"
              :disabled="!resolvedTaskId"
              @click="handleRecordPay"
            >
              提交支付并办结
            </Button>
          </Space>
          <div v-if="!resolvedTaskId" class="mt-2 text-sm text-orange-600">
            未拿到 taskId，请从「我的待办」进入
          </div>
        </Card>

        <Descriptions bordered :column="2" size="small">
          <Descriptions.Item label="单号">{{ detail.applicationNo }}</Descriptions.Item>
          <Descriptions.Item label="状态">{{ detail.status }}</Descriptions.Item>
          <Descriptions.Item label="主体公司">
            {{ detail.entityCompanyName || '历史未记录' }}
          </Descriptions.Item>
          <Descriptions.Item label="收款方">{{ detail.payeeName }}</Descriptions.Item>
          <Descriptions.Item label="金额">
            {{ detail.applyAmount }} {{ detail.currency }}
          </Descriptions.Item>
          <Descriptions.Item label="事由">{{ detail.paymentReason }}</Descriptions.Item>
          <Descriptions.Item label="时效">{{ detail.paymentTiming }}</Descriptions.Item>
          <Descriptions.Item label="账户" :span="2">
            {{ detail.payeeBankName }} / {{ detail.payeeBankAccount }}
          </Descriptions.Item>
          <Descriptions.Item
            v-if="isFinanceNode && isApproval !== false"
            label="费用科目/性质"
          >
            {{ detail.accountingSubject || '-' }}
          </Descriptions.Item>
          <Descriptions.Item label="累计已付">
            {{ detail.cumulativePaid ?? '-' }}
          </Descriptions.Item>
          <Descriptions.Item label="依据" :span="2">
            {{ detail.evidenceFileUrls }}
          </Descriptions.Item>
          <Descriptions.Item label="支付日">{{ detail.actualPayDate || '-' }}</Descriptions.Item>
          <Descriptions.Item label="支付凭证">
            {{ detail.payVoucherUrl || '-' }}
          </Descriptions.Item>
        </Descriptions>
      </template>
      <div v-else-if="!loading" class="text-gray-500">
        无法加载详情。请确认 businessKey 与 formCustomViewPath 配置。
      </div>
    </Spin>
  </div>
</template>
