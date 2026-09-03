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
  Descriptions,
  Select,
  Spin,
  Table,
  Tag,
  message,
} from 'ant-design-vue';

import {
  confirmPaymentMaterials,
  getPaymentApplication,
  updatePaymentAccountingSubject,
} from '#/api/finance/payment-application';
import type { DefaultOptionType } from 'ant-design-vue/es/select';

import { getDictOptions } from '@vben/hooks';
import { FilePreviewList } from '#/components/upload';
import { previewAuthUrl } from '#/utils/file-preview';
import { displayDate } from '#/utils/display-time';
import {
  financeProductLabel,
  financeReasonLabel,
  financeStatusLabel,
  financeTimingLabel,
} from '#/views/finance/shared/display-labels';
import PrintVoucher from '../modules/print-voucher.vue';
import PredocOverlay from '../modules/predoc-overlay.vue';

defineOptions({ name: 'FinancePaymentApplicationBpmDetail' });

function dictOptions(dictType: string): DefaultOptionType[] {
  return getDictOptions(dictType).map((d) => ({
    label: d.label,
    value: d.value as string | number,
  }));
}

const props = withDefaults(
  defineProps<{
    activityNodes?: any[];
    id?: number | string;
    isApproval?: boolean;
    mode?: 'cashier' | 'finance' | 'readonly';
    nodeKey?: string;
    nodeKeyName?: string;
    processDefinition?: any;
    processInstance?: any;
    taskId?: string;
  }>(),
  { mode: 'readonly' },
);

const predocOpen = ref(false);
const overlayKind = ref<'PURCHASE' | 'LEASE' | 'BUSINESS'>();

function openPredoc(kind: 'PURCHASE' | 'LEASE' | 'BUSINESS') {
  overlayKind.value = kind;
  predocOpen.value = true;
}

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

function resolveTaskId(): string {
  if (props.taskId) return String(props.taskId);
  const q = route.query.taskId;
  if (q !== null && q !== undefined && q !== '') {
    return String(Array.isArray(q) ? q[0] : q);
  }
  return '';
}

const resolvedTaskId = computed(() => resolveTaskId());
const isFinanceNode = computed(() => props.mode === 'finance');
const isCashierNode = computed(() => props.mode === 'cashier');

async function onConfirmMaterials() {
  const id = detail.value?.id;
  const tid = resolvedTaskId.value;
  if (!id || !tid) {
    message.error('缺少任务 id');
    return;
  }
  await confirmPaymentMaterials(id, tid);
  message.success('已确认资料齐全');
  await loadData();
}

async function previewEvidence(url: string) {
  try {
    await previewAuthUrl(url);
  } catch (e: any) {
    message.error(e?.message || '预览失败');
  }
}

const canResubmit = computed(() => {
  const d = detail.value;
  if (!d) return false;
  const uid = userStore.userInfo?.id;
  if (uid == null || Number(d.applicantUserId) !== Number(uid)) return false;
  return d.status === 'REJECTED' || d.materialsStatus === 'WAIT_INVOICE';
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
          <Tag>{{ financeStatusLabel(detail.status) }}</Tag>
          <Tag v-if="detail.materialsStatus === 'WAIT_INVOICE'" color="red">待补票</Tag>
          <Button
            v-if="detail.materialsStatus === 'WAIT_INVOICE' && isCashierNode"
            class="print:hidden"
            size="small"
            type="primary"
            @click="onConfirmMaterials"
          >确认资料齐全</Button>
          <PrintVoucher :detail="detail" />
          <Tag v-if="isFinanceNode || isCashierNode" color="orange">
            {{ props.nodeKeyName || props.mode }}
          </Tag>
        </div>

        <Alert
          v-if="canResubmit"
          class="mb-4"
          type="warning"
          show-icon
          :message="detail.materialsStatus === 'WAIT_INVOICE' ? '待补票' : '本单已驳回'"
          :description="detail.materialsStatus === 'WAIT_INVOICE' ? '请补传发票/结算单后从待办提交，将回流至出纳确认。' : '请修改后重提，将整链重批。'"
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

        <Descriptions bordered :column="2" size="small" class="mb-4">
          <Descriptions.Item label="单号">{{ detail.applicationNo }}</Descriptions.Item>
          <Descriptions.Item label="状态">{{ financeStatusLabel(detail.status) }}</Descriptions.Item>
          <Descriptions.Item label="主体公司">
            {{ detail.entityCompanyName || '历史未记录' }}
          </Descriptions.Item>
          <Descriptions.Item label="收款方">{{ detail.payeeName || '-' }}</Descriptions.Item>
          <Descriptions.Item label="金额">
            {{ detail.applyAmount }} {{ detail.currency }}
          </Descriptions.Item>
          <Descriptions.Item
            v-if="detail.paymentReason === 'BUSINESS'"
            label="产品类型"
          >
            {{ financeProductLabel(detail.costProject) }}
          </Descriptions.Item>
          <Descriptions.Item label="事由">{{ financeReasonLabel(detail.paymentReason) }}</Descriptions.Item>
          <Descriptions.Item v-if="detail.purchaseProcessInstanceId" label="采购前置">
            <span>{{ detail.purchaseSnapshot || detail.purchaseProcessInstanceId }}</span>
            <Button type="link" class="px-1" @click="openPredoc('PURCHASE')">查看采购申请</Button>
          </Descriptions.Item>
          <Descriptions.Item v-if="detail.leaseContractApplicationId" label="租赁合同">
            <span>{{ detail.leaseContractApplicationId }}</span>
            <Button type="link" class="px-1" @click="openPredoc('LEASE')">查看租赁合同</Button>
          </Descriptions.Item>
          <Descriptions.Item v-if="detail.relatedContractApplicationId" label="合同签约">
            <span>{{ detail.relatedContractApplicationId }}</span>
            <Button type="link" class="px-1" @click="openPredoc('BUSINESS')">查看付款业务合同</Button>
          </Descriptions.Item>
          <Descriptions.Item label="时效">{{ financeTimingLabel(detail.paymentTiming) }}</Descriptions.Item>
          <Descriptions.Item label="期间">{{ detail.periodLabel || '-' }}</Descriptions.Item>
          <Descriptions.Item label="账户" :span="2">
            {{ detail.payeeBankName || '-' }} / {{ detail.payeeBankAccount || '-' }}
          </Descriptions.Item>
          <Descriptions.Item label="已登记支付合计">
            {{ detail.paidLineSum ?? 0 }}
          </Descriptions.Item>
          <Descriptions.Item label="支付日">{{ displayDate(detail.actualPayDate) }}</Descriptions.Item>
          <Descriptions.Item label="依据" :span="2">
            <FilePreviewList :value="detail.evidenceFileUrls" />
          </Descriptions.Item>
          <Descriptions.Item label="特殊说明" :span="2">
            <span class="whitespace-pre-wrap break-words">{{
              detail.specialNote || '—'
            }}</span>
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
              { title: '回单', dataIndex: 'payVoucherUrl', key: 'f' },
            ]"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'f'">
                <FilePreviewList :value="record.payVoucherUrl" />
              </template>
            </template>
          </Table>
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
              { title: '公积金', dataIndex: 'housingFundAmount', key: 'hf' },
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
    <PredocOverlay
      v-model:open="predocOpen"
      :kind="overlayKind"
      :purchase-process-instance-id="detail?.purchaseProcessInstanceId"
      :purchase-snapshot="detail?.purchaseSnapshot"
      :contract-id="
        overlayKind === 'LEASE'
          ? detail?.leaseContractApplicationId
          : detail?.relatedContractApplicationId
      "
    />
  </div>
</template>
