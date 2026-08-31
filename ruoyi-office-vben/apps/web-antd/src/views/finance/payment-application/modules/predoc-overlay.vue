<script lang="ts" setup>
import { computed, nextTick, ref, shallowRef, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';
import { BpmModelFormType } from '@vben/constants';

import { Descriptions, Modal, Spin } from 'ant-design-vue';

import { getProcessInstance } from '#/api/bpm/processInstance';
import { setConfAndFields2 } from '#/components/form-create';
import { registerComponent } from '#/utils';
import ContractInfo from '#/views/finance/contract-application/modules/info.vue';

defineOptions({ name: 'FinancePaymentPredocOverlay' });

const open = defineModel<boolean>('open', { default: false });

const props = defineProps<{
  kind?: 'PURCHASE' | 'LEASE' | 'BUSINESS';
  purchaseProcessInstanceId?: string;
  purchaseSnapshot?: string;
  contractId?: number;
}>();

const loading = ref(false);
const loadError = ref(false);
const processName = ref('');
const formVariables = ref<Record<string, any>>({});
const fApi = ref<any>();
const detailForm = ref({
  rule: [] as any[],
  option: {} as any,
  value: {} as any,
});
const BusinessFormComponent = shallowRef<any>(null);
const businessKey = ref('');
const formType = ref<number>();

const title = computed(() => {
  if (props.kind === 'LEASE') return '租赁合同';
  if (props.kind === 'BUSINESS') return '付款业务合同';
  return '采购申请';
});

const isPurchase = computed(() => props.kind === 'PURCHASE');
const isContract = computed(() => props.kind === 'LEASE' || props.kind === 'BUSINESS');

const varEntries = computed(() =>
  Object.entries(formVariables.value || {}).filter(([, v]) => v != null && v !== ''),
);

const [ContractModal, contractModalApi] = useVbenModal({
  connectedComponent: ContractInfo,
  destroyOnClose: true,
  onOpenChange(isOpen: boolean) {
    if (!isOpen && isContract.value) {
      open.value = false;
    }
  },
});

watch(
  () => [open.value, props.kind, props.contractId] as const,
  ([isOpen, kind, contractId]) => {
    if (!isOpen || (kind !== 'LEASE' && kind !== 'BUSINESS')) return;
    if (contractId == null) return;
    contractModalApi.setData({ id: contractId, readOnly: true });
    contractModalApi.open();
  },
);

watch(
  () => [open.value, props.kind, props.purchaseProcessInstanceId] as const,
  async ([isOpen, kind, pi]) => {
    if (!isOpen || kind !== 'PURCHASE') return;
    loadError.value = false;
    formVariables.value = {};
    processName.value = '';
    BusinessFormComponent.value = null;
    businessKey.value = '';
    formType.value = undefined;
    detailForm.value = { rule: [], option: {}, value: {} };
    if (!pi) {
      loadError.value = true;
      return;
    }
    loading.value = true;
    try {
      const inst = await getProcessInstance(pi as any);
      if (!inst) {
        loadError.value = true;
        return;
      }
      processName.value = inst.name || '';
      formVariables.value = inst.formVariables || {};
      businessKey.value = String(inst.businessKey || '');
      const def = (inst as any).processDefinition || {};
      formType.value = def.formType;
      if (def.formType === BpmModelFormType.NORMAL && def.formConf && def.formFields) {
        setConfAndFields2(
          detailForm,
          def.formConf,
          def.formFields,
          inst.formVariables,
        );
        await nextTick();
        fApi.value?.btn?.show(false);
        fApi.value?.resetBtn?.show(false);
        fApi.value?.disabled?.(true);
      } else if (def.formCustomViewPath) {
        BusinessFormComponent.value = registerComponent(def.formCustomViewPath);
      } else if (!Object.keys(formVariables.value).length) {
        loadError.value = true;
      }
    } catch {
      loadError.value = true;
    } finally {
      loading.value = false;
    }
  },
);
</script>

<template>
  <ContractModal />
  <Modal
    v-if="isPurchase"
    v-model:open="open"
    :title="title"
    width="800px"
    :footer="null"
    destroy-on-close
  >
    <Spin :spinning="loading">
      <div v-if="loadError">
        <div>无法加载前置单据</div>
        <div v-if="purchaseSnapshot" class="mt-2 text-sm">{{ purchaseSnapshot }}</div>
      </div>
      <div v-else-if="formType === BpmModelFormType.NORMAL && detailForm.rule.length">
        <form-create
          v-model="detailForm.value"
          v-model:api="fApi"
          :option="detailForm.option"
          :rule="detailForm.rule"
        />
      </div>
      <component
        :is="BusinessFormComponent"
        v-else-if="BusinessFormComponent"
        :id="businessKey"
        :is-approval="false"
      />
      <Descriptions v-else bordered size="small" :column="1">
        <Descriptions.Item v-if="processName" label="流程">{{ processName }}</Descriptions.Item>
        <Descriptions.Item v-for="[k, v] in varEntries" :key="k" :label="k">
          {{ v }}
        </Descriptions.Item>
      </Descriptions>
    </Spin>
  </Modal>
</template>
