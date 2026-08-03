<script lang="ts" setup>
/**
 * 合同签约 · BPM 自定义表单「查看」组件。
 * 由 processInstance/detail 经 formCustomViewPath 动态加载，
 * props.id = processInstance.businessKey（合同申请主键）。
 * 只读展示；通过/驳回走详情页操作条。
 */
import type { FinanceContractApplicationApi } from '#/api/finance/contract-application';

import { onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { formatDateTime } from '@vben/utils';

import {
  Descriptions,
  DescriptionsItem,
  Spin,
  Tag,
  message,
} from 'ant-design-vue';

import { getContractApplication } from '#/api/finance/contract-application';

defineOptions({ name: 'FinanceContractApplicationBpmInfo' });

const props = defineProps<{
  activityNodes?: any[];
  id?: number | string;
  isApproval?: boolean;
  nodeKey?: string;
  nodeKeyName?: string;
  processDefinition?: any;
  processInstance?: any;
}>();

const route = useRoute();
const loading = ref(false);
const detail = ref<FinanceContractApplicationApi.Application | null>(null);

function resolveId(): number | undefined {
  if (props.id != null && props.id !== '') {
    const n = typeof props.id === 'string' ? Number(props.id) : props.id;
    return Number.isFinite(n) ? n : undefined;
  }
  const q = route.query.id;
  if (q != null && q !== '') {
    const n = Number(q);
    return Number.isFinite(n) ? n : undefined;
  }
  return undefined;
}

function statusText(row: FinanceContractApplicationApi.Application) {
  if (row.voided) return '已作废';
  if (row.approvalStatus === 'PENDING') {
    return row.currentNodeName
      ? `审批中 · ${row.currentNodeName}`
      : '审批中';
  }
  const map: Record<string, string> = {
    APPROVED: '已通过',
    REJECTED: '已驳回',
    CANCELLED: '已取消',
  };
  return map[row.approvalStatus || ''] || row.approvalStatus || '-';
}

function displayTime(val?: string | number | null) {
  if (val == null || val === '') return '-';
  return (formatDateTime(val as any) as string) || String(val);
}

async function loadData() {
  const id = resolveId();
  if (id == null) {
    detail.value = null;
    return;
  }
  loading.value = true;
  try {
    detail.value = await getContractApplication(id);
  } catch (error) {
    detail.value = null;
    const msg =
      error instanceof Error ? error.message : '加载合同签约详情失败';
    message.error(msg);
  } finally {
    loading.value = false;
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
        </div>

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
          <DescriptionsItem label="签约主体">
            {{ detail.signCompany || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="文件名称" :span="2">
            {{ detail.fileName || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="文件类型">
            {{ detail.fileType || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="产品类型">
            {{ detail.productType || '-' }}
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
          <DescriptionsItem label="起止日期" :span="2">
            {{ detail.startDate || '-' }} ~ {{ detail.endDate || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="电子版" :span="2">
            {{ detail.draftFileUrl || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="用印扫描件" :span="2">
            {{ detail.sealFileUrl || '-' }}
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
