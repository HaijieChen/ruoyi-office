<script lang="ts" setup>
import type { FinanceContractApplicationApi } from '#/api/finance/contract-application';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';
import { formatDateTime } from '@vben/utils';

import { Descriptions, Divider, Spin, message } from 'ant-design-vue';

import { getContractApplication } from '#/api/finance/contract-application';
import ApprovalOverviewPanel from '#/views/bpm/processInstance/detail/modules/approval-overview-panel.vue';

defineOptions({ name: 'FinanceContractApplicationInfo' });

const detail = ref<FinanceContractApplicationApi.Application | null>(null);
const loading = ref(false);

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

function displayTime(val?: null | number | string) {
  if (val == null || val === '') return '-';
  return (formatDateTime(val as any) as string) || String(val);
}

const [Modal, modalApi] = useVbenModal({
  showConfirmButton: false,
  cancelText: '关闭',
  class: 'w-[960px]',
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      detail.value = null;
      return;
    }
    const data = modalApi.getData<{ id?: number }>();
    if (!data?.id) return;
    loading.value = true;
    try {
      detail.value = await getContractApplication(data.id);
    } catch (error: any) {
      detail.value = null;
      message.error(
        error?.msg || error?.message || '加载合同签约详情失败',
      );
    } finally {
      loading.value = false;
    }
  },
});
</script>

<template>
  <Modal title="合同签约详情">
    <Spin :spinning="loading">
      <Descriptions v-if="detail" bordered :column="2" size="small">
        <Descriptions.Item label="业务单号">
          {{ detail.applicationNo }}
        </Descriptions.Item>
        <Descriptions.Item label="状态">
          {{ statusText(detail) }}
        </Descriptions.Item>
        <Descriptions.Item label="对方">
          {{ detail.counterpartyName }}
        </Descriptions.Item>
        <Descriptions.Item label="签约主体">
          {{ detail.signCompany }}
        </Descriptions.Item>
        <Descriptions.Item label="文件名称" :span="2">
          {{ detail.fileName }}
        </Descriptions.Item>
        <Descriptions.Item label="文件类型">
          {{ detail.fileType }}
        </Descriptions.Item>
        <Descriptions.Item label="产品类型">
          {{ detail.productType || '-' }}
        </Descriptions.Item>
        <Descriptions.Item label="合同金额">
          {{ detail.amountNa ? '不适用' : (detail.contractAmount ?? '-') }}
        </Descriptions.Item>
        <Descriptions.Item label="返点比例">
          {{ detail.rebateRatio }}
        </Descriptions.Item>
        <Descriptions.Item label="结算方式">
          {{ detail.settlementMethod }}
        </Descriptions.Item>
        <Descriptions.Item label="份数">
          {{ detail.copyCount }}
        </Descriptions.Item>
        <Descriptions.Item label="印章类型" :span="2">
          {{ detail.sealTypes }}
        </Descriptions.Item>
        <Descriptions.Item label="邮寄">
          {{ detail.needMail ? '是' : '否' }}
        </Descriptions.Item>
        <Descriptions.Item label="邮寄地址">
          {{ detail.mailAddress || '-' }}
        </Descriptions.Item>
        <Descriptions.Item label="起止日期" :span="2">
          {{ detail.startDate || '-' }} ~ {{ detail.endDate || '-' }}
        </Descriptions.Item>
        <Descriptions.Item label="电子版" :span="2">
          {{ detail.draftFileUrl || '-' }}
        </Descriptions.Item>
        <Descriptions.Item label="用印扫描件" :span="2">
          {{ detail.sealFileUrl || '-' }}
        </Descriptions.Item>
        <Descriptions.Item label="归档时间">
          {{ displayTime(detail.archivedAt) }}
        </Descriptions.Item>
        <Descriptions.Item label="邮寄单号">
          {{ detail.mailTrackingNo || '-' }}
        </Descriptions.Item>
        <Descriptions.Item label="创建时间">
          {{ displayTime(detail.createTime) }}
        </Descriptions.Item>
        <Descriptions.Item label="流程实例">
          {{ detail.processInstanceId || '-' }}
        </Descriptions.Item>
        <Descriptions.Item label="备注" :span="2">
          {{ detail.remark || '-' }}
        </Descriptions.Item>
      </Descriptions>

      <template v-if="detail">
        <Divider orientation="left" class="!mt-6">审批全貌</Divider>
        <ApprovalOverviewPanel
          :process-instance-id="detail.processInstanceId"
        />
      </template>
    </Spin>
  </Modal>
</template>
