<script lang="ts" setup>
import type { Rule } from 'ant-design-vue/es/form';

import type { FinanceBusinessOrderApi } from '#/api/finance/business-order';

import { computed, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import {
  DatePicker,
  Form,
  Input,
  InputNumber,
  Select,
  message,
  Textarea,
} from 'ant-design-vue';

import {
  createBusinessOrder,
  getBusinessOrder,
  updateBusinessOrder,
} from '#/api/finance/business-order';
import { listSelectableContractsForBo } from '#/api/finance/contract-application';
import { getSimpleCompanyList } from '#/api/system/dept';
import { $t } from '#/locales';
import { financeProductLabel } from '#/views/finance/shared/display-labels';
import { useBusinessStaffField } from '#/views/finance/shared/use-business-staff';

import {
  resolveEditOpenProductType,
  resolveProductAfterContractChange,
} from '../product-display';

defineOptions({ name: 'FinanceBusinessOrderForm' });

const emit = defineEmits(['success']);

interface CompanyOption {
  label: string;
  value: number;
}

interface ContractOption {
  label: string;
  value: number;
}

/** Local form state: date fields stored as YYYY-MM-DD strings via value-format on DatePicker. */
interface FormData {
  id?: number;
  entityCompanyDeptId?: number;
  entityCompanyName?: string;
  contractProcessId?: string;
  contractApplicationId?: number;
  orderDate?: string;
  /** 只读展示：来自合同 productType（不提交） */
  productType?: string;
  contactPerson?: string;
  executionStartDate?: string;
  executionEndDate?: string;
  payerName?: string;
  signedExecutionAmount?: number;
  discountRate?: number;
  currency?: string;
  remark?: string;
  // read-only server fields (displayed only, never sent)
  settlementAmount?: number;
  orderNo?: string;
  importDate?: string;
  importer?: string;
  applicant?: string;
  businessStaffUserId?: number;
}

const { staffOptions, loadBusinessStaff } = useBusinessStaffField();
const formRef = ref();
const formData = ref<FormData>({});
const companyOptions = ref<CompanyOption[]>([]);
const contractOptions = ref<ContractOption[]>([]);
const loadingCompany = ref(false);
const loadingContract = ref(false);
/** 合同 id → 对方名称（回填源） */
const contractCounterpartyMap = ref<Record<number, string>>({});
/** 合同 id → 产品类型（只读展示） */
const contractProductMap = ref<Record<number, string>>({});
/** 打开编辑时已保存的合同 id（复审 #10：选回原合同时恢复快照） */
const savedContractId = ref<number | null | undefined>();
/** 打开编辑时已保存的产品快照 */
const savedProductSnapshot = ref<string | undefined>();
/** 用户是否手改过付款方；true 时换合同不覆盖 */
const payerDirty = ref(false);
/** 系统回填中，忽略 Input 可能触发的 change */
let payerAutofilling = false;

const rules: Record<string, Rule[]> = {
  entityCompanyDeptId: [
    { required: true, message: '主体公司不能为空', trigger: 'change' },
  ],
  contractApplicationId: [
    {
      validator: async (_: Rule, v: number | undefined) => {
        // 新建必须选合同；历史空编辑可不选（后端允许只改其它字段）
        if (formData.value?.id) return Promise.resolve();
        if (v == null) return Promise.reject('请选择已通过的合同签约申请');
        return Promise.resolve();
      },
      trigger: 'change',
    },
  ],
  orderDate: [{ required: true, message: '签单日期不能为空', trigger: 'change' }],
  contactPerson: [{ required: true, message: '联系人不能为空', trigger: 'blur' }],
  executionStartDate: [{ required: true, message: '执行开始日期不能为空', trigger: 'change' }],
  executionEndDate: [{ required: true, message: '执行结束日期不能为空', trigger: 'change' }],
  signedExecutionAmount: [
    { required: true, message: '签约执行金额不能为空' },
    {
      validator: (_: Rule, v: number) =>
        v > 0 ? Promise.resolve() : Promise.reject('签约执行金额必须大于0'),
      trigger: 'change',
    },
  ],
  currency: [{ required: true, message: '请选择币种', trigger: 'change' }],
  discountRate: [
    {
      validator: (_: Rule, v: number | undefined) => {
        if (v === undefined || v === null) return Promise.resolve();
        if (v < 0) return Promise.reject('折扣不能为负数');
        const amt = formData.value.signedExecutionAmount ?? 0;
        if (v > amt) return Promise.reject('折扣不能超过签约执行金额');
        return Promise.resolve();
      },
      trigger: 'change',
    },
  ],
};

const isEdit = computed(() => !!formData.value?.id);
const getTitle = computed(() =>
  isEdit.value
    ? $t('ui.actionTitle.edit', ['签单记录'])
    : $t('ui.actionTitle.create', ['签单记录']),
);

function resetForm() {
  formData.value = {};
  payerDirty.value = false;
  contractCounterpartyMap.value = {};
  contractProductMap.value = {};
  savedContractId.value = undefined;
  savedProductSnapshot.value = undefined;
  formRef.value?.resetFields();
}

function onPayerNameUserEdit() {
  if (payerAutofilling) return;
  payerDirty.value = true;
}

/** 未手改时，按所选合同对方名回填付款方 */
function maybeAutofillPayerFromContract(contractId?: number | null) {
  if (payerDirty.value) return;
  if (contractId == null) return;
  const name = contractCounterpartyMap.value[contractId];
  if (!name || !String(name).trim()) return;
  payerAutofilling = true;
  formData.value.payerName = String(name).trim();
  payerDirty.value = false;
  queueMicrotask(() => {
    payerAutofilling = false;
  });
}

/**
 * 仅在用户显式切换合同时刷新只读产品展示。
 * 选回已保存合同 → 恢复已保存快照；否则取合同当前产品（#10）。
 */
function refreshProductOnContractChange(contractId?: number | null) {
  const current =
    contractId != null && contractProductMap.value[contractId]
      ? contractProductMap.value[contractId]
      : undefined;
  formData.value.productType = resolveProductAfterContractChange({
    selectedContractId: contractId,
    savedContractId: savedContractId.value,
    savedProductSnapshot: savedProductSnapshot.value,
    contractCurrentProduct: current,
  });
}

function formatDateField(value: unknown): string | undefined {
  if (value == null || value === '') {
    return undefined;
  }
  if (typeof value === 'string') {
    return value.length >= 10 ? value.slice(0, 10) : value;
  }
  if (Array.isArray(value) && value.length >= 3) {
    const [y, m, d] = value as number[];
    return `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
  }
  return undefined;
}

async function loadCompanyOptions() {
  loadingCompany.value = true;
  try {
    const list = (await getSimpleCompanyList()) || [];
    companyOptions.value = list
      .filter((c) => c.id != null)
      .map((c) => ({
        value: c.id as number,
        label: c.name,
      }));
  } finally {
    loadingCompany.value = false;
  }
}

async function loadContractOptions() {
  loadingContract.value = true;
  try {
    const list = (await listSelectableContractsForBo()) || [];
    const counterpartyMap: Record<number, string> = {};
    const productMap: Record<number, string> = {};
    contractOptions.value = list
      .filter((c) => c.id != null)
      .map((c) => {
        const id = c.id as number;
        if (c.counterpartyName && String(c.counterpartyName).trim()) {
          counterpartyMap[id] = String(c.counterpartyName).trim();
        }
        if (c.productType && String(c.productType).trim()) {
          productMap[id] = String(c.productType).trim();
        }
        const no = c.applicationNo || String(c.id);
        const party = c.counterpartyName ? String(c.counterpartyName).trim() : '-';
        const product = financeProductLabel(c.productType);
        // 合同单号｜对方｜产品
        return {
          value: id,
          label: `${no}｜${party}｜${product}`,
        };
      });
    contractCounterpartyMap.value = counterpartyMap;
    contractProductMap.value = productMap;
  } finally {
    loadingContract.value = false;
  }
}

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    await formRef.value?.validate();
    modalApi.lock();
    // EXP-70：payload 不发送产品；服务端从合同派生
    const saveData: FinanceBusinessOrderApi.SaveForm = {
      id: formData.value.id,
      entityCompanyDeptId: formData.value.entityCompanyDeptId!,
      // 正式关联只提交 contractApplicationId；legacy 流程文本不再从 UI 写入
      contractApplicationId: formData.value.contractApplicationId,
      orderDate: formData.value.orderDate!,
      contactPerson: formData.value.contactPerson!,
      executionStartDate: formData.value.executionStartDate!,
      executionEndDate: formData.value.executionEndDate!,
      payerName: formData.value.payerName,
      signedExecutionAmount: formData.value.signedExecutionAmount!,
      currency: (formData.value.currency || 'CNY').toUpperCase(),
      discountRate: formData.value.discountRate,
      remark: formData.value.remark,
      businessStaffUserId: formData.value.businessStaffUserId,
    };
    try {
      await (saveData.id ? updateBusinessOrder(saveData) : createBusinessOrder(saveData));
      await modalApi.close();
      emit('success');
      message.success($t('ui.actionMessage.operationSuccess'));
    } finally {
      modalApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      resetForm();
      companyOptions.value = [];
      contractOptions.value = [];
      return;
    }
    payerDirty.value = false;
    const defaultStaff = await loadBusinessStaff();
    await Promise.all([loadCompanyOptions(), loadContractOptions()]);
    const data = modalApi.getData<{ id?: number }>();
    if (!data?.id) {
      formData.value.businessStaffUserId = defaultStaff;
      // 新建：不预填；用户选合同时 @change 回填
      return;
    }
    modalApi.lock();
    try {
      const detail = await getBusinessOrder(data.id);
      formData.value = {
        ...detail,
        // 后端可能返回 LocalDate 数组，DatePicker 需要字符串
        orderDate: formatDateField(detail.orderDate),
        executionStartDate: formatDateField(detail.executionStartDate),
        executionEndDate: formatDateField(detail.executionEndDate),
        importDate: formatDateField(detail.importDate),
        importer: detail.importerName || String(detail.importerId ?? ''),
        applicant: detail.applicantName || String(detail.applicantUserId ?? ''),
        // 只读产品：仅详情快照/legacy（EXP-70 复审 #2，勿用合同当前值）
        productType: resolveEditOpenProductType(detail),
      };
      // #10：记住已保存合同与快照，供 A→B→A 恢复
      savedContractId.value = detail.contractApplicationId ?? null;
      savedProductSnapshot.value = resolveEditOpenProductType(detail);
      // 编辑打开：保留库中付款方，不覆盖
      payerDirty.value = false;
      // 详情中的主体公司若不在启用列表中，补一条选项以便展示
      if (
        detail.entityCompanyDeptId != null &&
        !companyOptions.value.some((o) => o.value === detail.entityCompanyDeptId)
      ) {
        companyOptions.value = [
          {
            value: detail.entityCompanyDeptId,
            label:
              detail.entityCompanyName ||
              `公司 #${detail.entityCompanyDeptId}`,
          },
          ...companyOptions.value,
        ];
      }
      // 已关联合同若不在可选列表（历史/他人员），补一条便于编辑展示
      // 标签中的产品用详情快照（非合同当前值），避免误导
      if (
        detail.contractApplicationId != null &&
        !contractOptions.value.some((o) => o.value === detail.contractApplicationId)
      ) {
        const product = detail.productType || detail.productName || '-';
        const no = detail.contractApplicationNo || `#${detail.contractApplicationId}`;
        contractOptions.value = [
          {
            value: detail.contractApplicationId,
            label: `${no}｜-｜${product}`,
          },
          ...contractOptions.value,
        ];
      }
      // EXP-70：编辑初次打开只展示详情快照；显式换合同见 onContractChange
    } finally {
      modalApi.unlock();
    }
  },
});

watch(
  () => formData.value.signedExecutionAmount,
  () => {
    if (formData.value.discountRate !== undefined) {
      formRef.value?.validateFields(['discountRate']);
    }
  },
);

/** 仅用户变更合同时回填；选回已保存合同则恢复已保存快照（#10） */
function onContractChange(id: unknown) {
  const contractId =
    id === null || id === undefined || id === ''
      ? undefined
      : Number(id);
  const normalized =
    contractId !== undefined && Number.isFinite(contractId)
      ? contractId
      : undefined;
  maybeAutofillPayerFromContract(normalized);
  refreshProductOnContractChange(normalized);
}
</script>

<template>
  <Modal :title="getTitle" class="w-[640px]">
    <Form
      ref="formRef"
      :model="formData"
      :rules="rules"
      :label-col="{ span: 7 }"
      :wrapper-col="{ span: 15 }"
    >
      <template v-if="isEdit">
        <Form.Item label="订单编号">
          <Input :value="formData.orderNo" disabled />
        </Form.Item>
        <Form.Item label="导入日期">
          <Input :value="formData.importDate" disabled />
        </Form.Item>
        <Form.Item label="导入人">
          <Input :value="formData.importer" disabled />
        </Form.Item>
        <Form.Item label="提单人">
          <Input :value="formData.applicant" disabled />
        </Form.Item>
      </template>

      <Form.Item label="业务人员" name="businessStaffUserId">
        <Select
          v-model:value="formData.businessStaffUserId"
          class="w-full"
          show-search
          option-filter-prop="label"
          :options="staffOptions"
          placeholder="默认提单人，可改选任职公司员工"
        />
      </Form.Item>
      <Form.Item label="主体公司" name="entityCompanyDeptId">
        <Select
          v-model:value="formData.entityCompanyDeptId"
          class="w-full"
          show-search
          allow-clear
          :loading="loadingCompany"
          :options="companyOptions"
          option-filter-prop="label"
          placeholder="请选择组织架构中的公司"
          @dropdown-visible-change="
            (open: boolean) => {
              if (open && !companyOptions.length) loadCompanyOptions();
            }
          "
        />
      </Form.Item>
      <Form.Item label="合同签约申请" name="contractApplicationId">
        <Select
          v-model:value="formData.contractApplicationId"
          class="w-full"
          show-search
          allow-clear
          :loading="loadingContract"
          :options="contractOptions"
          option-filter-prop="label"
          placeholder="请选择已审批通过且本人申请的合同"
          @change="onContractChange"
          @dropdown-visible-change="
            (open: boolean) => {
              if (open && !contractOptions.length) loadContractOptions();
            }
          "
        />
      </Form.Item>
      <Form.Item label="签单日期" name="orderDate">
        <DatePicker
          v-model:value="formData.orderDate"
          value-format="YYYY-MM-DD"
          placeholder="请选择签单日期"
          style="width: 100%"
        />
      </Form.Item>
      <Form.Item label="产品/服务">
        <Input
          :value="financeProductLabel(formData.productType)"
          disabled
          placeholder="选择合同后自动带出，不可改"
        />
      </Form.Item>
      <Form.Item label="联系人" name="contactPerson">
        <Input v-model:value="formData.contactPerson" placeholder="请输入联系人" />
      </Form.Item>
      <Form.Item label="执行开始日期" name="executionStartDate">
        <DatePicker
          v-model:value="formData.executionStartDate"
          value-format="YYYY-MM-DD"
          placeholder="请选择执行开始日期"
          style="width: 100%"
        />
      </Form.Item>
      <Form.Item label="执行结束日期" name="executionEndDate">
        <DatePicker
          v-model:value="formData.executionEndDate"
          value-format="YYYY-MM-DD"
          placeholder="请选择执行结束日期"
          style="width: 100%"
        />
      </Form.Item>
      <Form.Item label="付款方" name="payerName">
        <Input
          v-model:value="formData.payerName"
          placeholder="选合同后自动带出，可改"
          @change="onPayerNameUserEdit"
          @input="onPayerNameUserEdit"
        />
      </Form.Item>
      <Form.Item label="签约执行金额" name="signedExecutionAmount">
        <InputNumber
          v-model:value="formData.signedExecutionAmount"
          :min="0.01"
          :precision="2"
          style="width: 100%"
          placeholder="0.00"
        />
      </Form.Item>
      <Form.Item label="折扣" name="discountRate">
        <InputNumber
          v-model:value="formData.discountRate"
          :min="0"
          :precision="2"
          style="width: 100%"
          placeholder="留空视为0"
        />
      </Form.Item>

      <Form.Item label="结算金额">
        <InputNumber
          v-if="isEdit"
          :value="formData.settlementAmount"
          :precision="2"
          style="width: 100%"
          disabled
        />
        <div v-else class="text-sm text-gray-400 py-1">
          保存后由后端 HALF_UP 精确计算
        </div>
      </Form.Item>

      <Form.Item label="备注" name="remark">
        <Textarea v-model:value="formData.remark" placeholder="可选" :rows="3" />
      </Form.Item>
    </Form>
  </Modal>
</template>
