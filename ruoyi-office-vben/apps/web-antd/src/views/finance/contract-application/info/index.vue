<script lang="ts" setup>
/**
 * 合同签约 · BPM 自定义表单「查看」组件。
 * 由 processInstance/detail 经 formCustomViewPath 动态加载，
 * props.id = processInstance.businessKey（合同申请主键）。
 * 审批节点只读；用印/邮寄节点展示 record* 执行面板。归档改到合同列表操作。
 */
import type { FinanceContractApplicationApi } from '#/api/finance/contract-application';

import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useTabs } from '@vben/hooks';
import { useUserStore } from '@vben/stores';
import { formatDateTime } from '@vben/utils';

import {
  Alert,
  Button,
  Card,
  Descriptions,
  DescriptionsItem,
  Input,
  message,
  Space,
  Spin,
  Tag,
} from 'ant-design-vue';

import {
  getContractApplication,
  recordContractMail,
  recordContractSeal,
} from '#/api/finance/contract-application';
import { FilePreviewList, FileUpload } from '#/components/upload';
import { displayDate } from '#/utils/display-time';
import { financeProductLabel } from '#/views/finance/shared/display-labels';

defineOptions({ name: 'FinanceContractApplicationBpmInfo' });

const props = defineProps<{
  activityNodes?: any[];
  id?: number | string;
  isApproval?: boolean;
  nodeKey?: string;
  nodeKeyName?: string;
  processDefinition?: any;
  processInstance?: any;
  /** 当前待办任务 id（待办进入时由路由/父页传入） */
  taskId?: string;
}>();
const EXEC_SEAL = 'taskSeal';
const EXEC_MAIL = 'taskMail';
const EXEC_KEYS = new Set([EXEC_MAIL, EXEC_SEAL]);

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const { closeCurrentTab } = useTabs();

const loading = ref(false);
const submitting = ref(false);
const detail = ref<FinanceContractApplicationApi.Application | null>(null);
/** FileUpload 可能返回 string 或 string[] */
const sealFileUrl = ref<string | string[]>('');
const mailTrackingNo = ref('');

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
  return '';
}

function resolveTaskId(): string {
  if (props.taskId) return String(props.taskId);
  const q = route.query.taskId;
  if (q !== null && q !== undefined && q !== '') {
    return String(Array.isArray(q) ? q[0] : q);
  }
  return '';
}

/** Resolve node/task without shadowing props */
const resolvedNodeKey = computed(() => resolveNodeKey());
const resolvedTaskId = computed(() => resolveTaskId());
const isExecNode = computed(() => EXEC_KEYS.has(resolvedNodeKey.value));
const isSealNode = computed(() => resolvedNodeKey.value === EXEC_SEAL);
const isMailNode = computed(() => resolvedNodeKey.value === EXEC_MAIL);

/** 已驳回且当前用户为发起人：展示「修改并重提」（C16；流程详情/我的流程入口） */
const canResubmit = computed(() => {
  const d = detail.value;
  if (!d || d.voided || d.approvalStatus !== 'REJECTED') return false;
  const uid = userStore.userInfo?.id;
  return uid != null && Number(d.applicantUserId) === Number(uid);
});

async function handleGoResubmit() {
  const id = resolveId() ?? detail.value?.id;
  if (id === undefined) {
    message.warning('缺少申请编号');
    return;
  }
  try {
    await closeCurrentTab();
  } catch {
    // ignore
  }
  await router.push({
    path: '/finance/contract-application',
    query: { openResubmit: String(id) },
  });
}

function statusText(row: FinanceContractApplicationApi.Application) {
  if (row.voided) return '已作废';
  if (row.approvalStatus === 'PENDING') {
    return row.currentNodeName ? `审批中 · ${row.currentNodeName}` : '审批中';
  }
  const map: Record<string, string> = {
    APPROVED: '已通过',
    REJECTED: '已驳回',
    CANCELLED: '已取消',
  };
  return map[row.approvalStatus || ''] || row.approvalStatus || '-';
}

function displayTime(val?: null | number | string) {
  if (val === null || val === undefined || val === '') return '-';
  return (formatDateTime(val as any) as string) || String(val);
}

function sealUrlForSubmit(): string {
  const v = sealFileUrl.value;
  if (Array.isArray(v)) {
    const first = v[0];
    return (first === null || first === undefined ? '' : String(first)).trim();
  }
  return String(v || '').trim();
}

/** 文本框绑定：始终为 string */
const sealUrlText = computed({
  get: () => sealUrlForSubmit(),
  set: (val: string) => {
    sealFileUrl.value = val ?? '';
  },
});

async function loadData() {
  const id = resolveId();
  if (id === undefined) {
    detail.value = null;
    return;
  }
  loading.value = true;
  try {
    detail.value = await getContractApplication(id);
    if (detail.value?.sealFileUrl) {
      sealFileUrl.value = detail.value.sealFileUrl;
    }
    if (detail.value?.mailTrackingNo) {
      mailTrackingNo.value = detail.value.mailTrackingNo;
    }
  } catch (error) {
    detail.value = null;
    const msg = error instanceof Error ? error.message : '加载合同签约详情失败';
    message.error(msg);
  } finally {
    loading.value = false;
  }
}

async function afterExecSuccess(tip: string) {
  message.success(tip);
  try {
    await closeCurrentTab();
  } catch {
    // ignore
  }
  try {
    await router.push({ path: '/bpm/task/todo' });
  } catch {
    // 关闭即可
  }
}

async function handleRecordSeal() {
  const id = resolveId();
  const tid = resolvedTaskId.value;
  const url = sealUrlForSubmit();
  if (id === undefined || !tid) {
    message.error('缺少申请或任务编号，请从待办进入');
    return;
  }
  if (!url) {
    message.warning('请先上传或填写用印扫描件');
    return;
  }
  submitting.value = true;
  try {
    await recordContractSeal(id, tid, url);
    await afterExecSuccess('用印登记成功，任务已推进');
  } catch (error) {
    const msg = error instanceof Error ? error.message : '用印登记失败';
    message.error(msg);
  } finally {
    submitting.value = false;
  }
}

async function handleRecordMail() {
  const id = resolveId();
  const tid = resolvedTaskId.value;
  const tracking = mailTrackingNo.value?.trim();
  if (id === undefined || !tid) {
    message.error('缺少申请或任务编号，请从待办进入');
    return;
  }
  if (!tracking) {
    message.warning('请填写邮寄单号');
    return;
  }
  submitting.value = true;
  try {
    await recordContractMail(id, tid, tracking);
    await afterExecSuccess('邮寄登记成功，流程将完成');
  } catch (error) {
    const msg = error instanceof Error ? error.message : '邮寄登记失败';
    message.error(msg);
  } finally {
    submitting.value = false;
  }
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
  <div class="contract-application-info p-4">
    <Spin :spinning="loading">
      <template v-if="detail">
        <div class="mb-4 flex flex-wrap items-center gap-2">
          <span class="text-base font-medium">合同签约申请</span>
          <Tag color="blue">{{ detail.applicationNo || '-' }}</Tag>
          <Tag>{{ statusText(detail) }}</Tag>
          <Tag v-if="detail.voided" color="red">已作废</Tag>
          <Tag v-if="isExecNode" color="orange">
            执行节点 · {{ nodeKeyName || resolvedNodeKey }}
          </Tag>
        </div>

        <!-- 拒绝后：流程已结束，须从业务台账改单 resubmit（整链重批） -->
        <Alert
          v-if="canResubmit"
          class="mb-4"
          type="warning"
          show-icon
          message="本单已驳回"
          description="请修改合同内容后重新提交，将按整条审批链重新审批（非流程内退回）。"
        >
          <template #action>
            <Button type="primary" @click="handleGoResubmit">
              修改并重提
            </Button>
          </template>
        </Alert>

        <!-- CS-R2：用印 / 归档 / 邮寄 执行面板 -->
        <Card
          v-if="isSealNode && isApproval !== false"
          class="mb-4"
          size="small"
          title="用印登记"
        >
          <Alert
            class="mb-3"
            type="info"
            show-icon
            message="请上传用印扫描件后提交。请勿使用底部通用「通过」——本节点须走用印登记。"
          />
          <div class="mb-3">
            <div class="mb-1 text-sm text-gray-600">用印扫描件</div>
            <FileUpload
              v-model:value="sealFileUrl"
              :max-number="1"
              :max-size="30"
              :multiple="false"
              help-text="支持 PDF/图片；提交后写入台账并完成用印任务"
            />
            <Input
              v-model:value="sealUrlText"
              class="mt-2"
              allow-clear
              placeholder="或直接粘贴扫描件 URL"
            />
          </div>
          <Space>
            <Button
              type="primary"
              :loading="submitting"
              :disabled="!resolvedTaskId"
              @click="handleRecordSeal"
            >
              提交用印登记
            </Button>
            <span v-if="!resolvedTaskId" class="text-sm text-red-500">
              未拿到 taskId，请从「我的待办」进入
            </span>
          </Space>
        </Card>


        <Card
          v-if="isMailNode && isApproval !== false"
          class="mb-4"
          size="small"
          title="邮寄登记"
        >
          <Alert
            class="mb-3"
            type="info"
            show-icon
            message="填写快递单号后提交。空单号不可提交；请勿使用底部通用「通过」。"
          />
          <div class="mb-3 max-w-md">
            <div class="mb-1 text-sm text-gray-600">邮寄单号</div>
            <Input
              v-model:value="mailTrackingNo"
              allow-clear
              placeholder="请输入快递/邮寄单号"
              :maxlength="64"
            />
          </div>
          <Space>
            <Button
              type="primary"
              :loading="submitting"
              :disabled="!resolvedTaskId || !mailTrackingNo?.trim()"
              @click="handleRecordMail"
            >
              提交邮寄登记
            </Button>
            <span v-if="!resolvedTaskId" class="text-sm text-red-500">
              未拿到 taskId，请从「我的待办」进入
            </span>
          </Space>
        </Card>

        <Descriptions bordered :column="2" size="small">
          <DescriptionsItem label="业务单号">
            {{ detail.applicationNo || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="状态">
            {{ statusText(detail) }}
          </DescriptionsItem>
          <DescriptionsItem label="对方">
            {{ detail.counterpartyName || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="主体公司">
            {{ detail.entityCompanyName || detail.signCompany || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="文件名称" :span="2">
            {{ detail.fileName || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="合同类型">
            {{ detail.fileType || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="产品类型">
            {{ financeProductLabel(detail.productType) }}
          </DescriptionsItem>
          <DescriptionsItem label="合同金额">
            {{
              detail.amountNa
                ? '不适用'
                : detail.contractAmount != null
                  ? `¥${Number(detail.contractAmount).toFixed(2)}`
                  : '-'
            }}
          </DescriptionsItem>
          <DescriptionsItem label="返点比例">
            {{ detail.rebateRatio || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="结算方式">
            {{ detail.settlementMethod || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="份数">
            {{ detail.copyCount ?? '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="印章类型" :span="2">
            {{ detail.sealTypes || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="邮寄">
            {{ detail.needMail ? '是' : '否' }}
          </DescriptionsItem>
          <DescriptionsItem label="邮寄地址">
            {{ detail.mailAddress || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="前置流程" :span="2">
            {{ detail.preProcessRef || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="起始日期">
            {{ displayDate(detail.startDate) }}
          </DescriptionsItem>
          <DescriptionsItem label="结束日期">
            {{ displayDate(detail.endDate) }}
          </DescriptionsItem>
          <DescriptionsItem label="电子版" :span="2">
            <FilePreviewList :value="detail.draftFileUrl" />
          </DescriptionsItem>
          <DescriptionsItem label="用印扫描件" :span="2">
            <FilePreviewList :value="detail.sealFileUrl" />
          </DescriptionsItem>
          <DescriptionsItem label="归档时间">
            {{ displayTime(detail.archivedAt) }}
          </DescriptionsItem>
          <DescriptionsItem label="邮寄单号">
            {{ detail.mailTrackingNo || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="创建时间">
            {{ displayTime(detail.createTime) }}
          </DescriptionsItem>
          <DescriptionsItem label="流程实例">
            {{ detail.processInstanceId || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="备注" :span="2">
            {{ detail.remark || '-' }}
          </DescriptionsItem>
        </Descriptions>
      </template>
      <div v-else-if="!loading" class="text-gray-500">
        未找到合同签约申请（id={{ resolveId() ?? '空' }}）。请确认流程
        businessKey 与 formCustomViewPath 配置。
      </div>
    </Spin>
  </div>
</template>
