<script lang="ts" setup>
import type { Rule } from 'ant-design-vue/es/form';

import { computed, onMounted, ref } from 'vue';

import { getDictOptions } from '@vben/hooks';
import { useUserStore } from '@vben/stores';

import {
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Select,
  message,
} from 'ant-design-vue';
import dayjs from 'dayjs';

import { getOutingPage } from '#/api/bpm/oa/outing';
import { getTripPage } from '#/api/bpm/oa/trip';
import {
  createNoInvoiceExpense,
  getExpenseReimbursement,
  getOccupiedPredocIds,
} from '#/api/finance/expense-reimbursement';
import { getEmployeeWageCardByUserId } from '#/api/hrm/employee';
import { getSimpleDeptList } from '#/api/system/dept';
import { getSimpleUserList } from '#/api/system/user';
import { FileUpload } from '#/components/upload';
import { mapWageCardToPayee } from '../payee-prefill';
import PredocOverlay from './predoc-overlay.vue';

defineOptions({ name: 'FinanceExpenseNoInvoiceFormBody' });

const emit = defineEmits<{
  predictChange: [vars: Record<string, unknown>];
  success: [];
}>();

const userStore = useUserStore();
const formRef = ref();
const submitting = ref(false);
const tripOptions = ref<{ label: string; value: string; type: 'TRIP'; billId?: number; city?: string }[]>([]);
const outingOptions = ref<{ label: string; value: string; type: 'OUTING'; billId?: number; city?: string }[]>([]);

interface LineRow {
  category?: string;
  feeDate?: string;
  amount?: number;
  predocType?: string;
  predocProcessInstanceId?: string;
  predocBillId?: number;
  remark?: string;
  stayCityTier?: string;
  overLimitReason?: string;
}

const formData = ref<{
  userNickname?: string;
  deptName?: string;
  entityCompanyName?: string;
  actualUserId?: number;
  periodLabel?: string;
  payeeAccountName?: string;
  payeeBankName?: string;
  payeeAccountNo?: string;
  extraAttachments: string[];
  lines: LineRow[];
}>({
  periodLabel: dayjs().format('YYYY-MM'),
  extraAttachments: [],
  lines: [{}],
});

const userOptionsAll = ref<{ label: string; value: number; deptId?: number; deptName?: string }[]>([]);
const deptById = ref<Record<number, { name?: string; parentId?: number; orgType?: string }>>({});

function companyOfDept(deptId?: number) {
  let id = deptId;
  for (let i = 0; i < 16 && id; i++) {
    const d = deptById.value[id];
    if (!d) return undefined;
    if (String(d.orgType) === '1') return d.name;
    if (!d.parentId || d.parentId === id) return undefined;
    id = d.parentId;
  }
  return undefined;
}

const categoryOptions = computed(() =>
  getDictOptions('finance_expense_category', 'string').map((d) => ({
    label: d.label,
    value: String(d.value),
  })),
);

async function applyPayeeFromUser(userId?: number) {
  if (userId == null || Number.isNaN(Number(userId))) {
    Object.assign(formData.value, mapWageCardToPayee(null));
    return;
  }
  try {
    const card = await getEmployeeWageCardByUserId(Number(userId));
    Object.assign(formData.value, mapWageCardToPayee(card));
  } catch {
    Object.assign(formData.value, mapWageCardToPayee(null));
  }
}

function applyLoginUser() {
  formData.value.userNickname = userStore.userInfo?.nickname || '';
  formData.value.deptName = (userStore.userInfo as any)?.deptName || '';
  formData.value.actualUserId = Number(userStore.userInfo?.id);
  formData.value.entityCompanyName =
    companyOfDept(Number((userStore.userInfo as any)?.deptId)) || '';
  void applyPayeeFromUser(formData.value.actualUserId);
}

function onActualUserChange(id?: number) {
  const hit = userOptionsAll.value.find((u) => u.value === Number(id));
  formData.value.deptName = hit?.deptName || formData.value.deptName;
  formData.value.entityCompanyName = companyOfDept(hit?.deptId) || '';
  void applyPayeeFromUser(id);
}

async function loadPredocOptions() {
  try {
    const [trips, outings, occupied] = await Promise.all([
      getTripPage({ pageNo: 1, pageSize: 50 }),
      getOutingPage({ pageNo: 1, pageSize: 50 }),
      getOccupiedPredocIds().catch(() => [] as string[]),
    ]);
    const occupiedSet = new Set((occupied || []).map((id) => String(id)));
    tripOptions.value = (trips?.list || [])
      .filter((t) => Number(t.status) === 2 && t.processInstanceId && !occupiedSet.has(String(t.processInstanceId)))
      .map((t) => ({
        value: String(t.processInstanceId),
        type: 'TRIP' as const,
        billId: t.id,
        city: t.destination,
        label: `出差#${t.id} ${t.destination || ''}`.trim(),
      }));
    outingOptions.value = (outings?.list || [])
      .filter((t) => Number(t.status) === 2 && t.processInstanceId && !occupiedSet.has(String(t.processInstanceId)))
      .map((t) => ({
        value: String(t.processInstanceId),
        type: 'OUTING' as const,
        billId: t.id,
        city: t.location,
        label: `外出#${t.id} ${t.location || ''}`.trim(),
      }));
  } catch {
    tripOptions.value = [];
    outingOptions.value = [];
  }
}

function needsPredoc(line: LineRow) {
  return line.category === 'travel' || line.category === 'transport';
}

function lineDetailsEnabled(line: LineRow) {
  if (!line.category) return false;
  if (needsPredoc(line)) return !!line.predocProcessInstanceId;
  return true;
}

const predocOpen = ref(false);
const overlayType = ref<string>();
const overlayBillId = ref<number>();

function openPredoc(line: LineRow) {
  overlayType.value = line.predocType;
  overlayBillId.value = line.predocBillId;
  predocOpen.value = true;
}

function predocLinkLabel(line: LineRow) {
  return line.predocType === 'OUTING' ? '查看出外申请' : '查看出差申请';
}

function onCategoryChange(index: number) {
  const line = formData.value.lines[index];
  if (!line) return;
  line.predocProcessInstanceId = undefined;
  line.predocType = undefined;
  line.predocBillId = undefined;
  line.stayCityTier = undefined;
  line.overLimitReason = undefined;
}

function predocOptions(category?: string) {
  if (category === 'travel') return tripOptions.value;
  if (category === 'transport') return [...tripOptions.value, ...outingOptions.value];
  return [];
}

function onPredocChange(index: number, processInstanceId?: string) {
  const line = formData.value.lines[index];
  if (!line) return;
  line.predocProcessInstanceId = processInstanceId;
  const hit = [...tripOptions.value, ...outingOptions.value].find((o) => o.value === processInstanceId);
  line.predocType = hit?.type;
  line.predocBillId = hit?.billId;
  line.stayCityTier = ['北京', '上海', '广州', '深圳'].includes(String(hit?.city || ''))
    ? 'T1'
    : hit?.city
      ? 'OTHER'
      : undefined;
}

function predocCity(line: LineRow) {
  return [...tripOptions.value, ...outingOptions.value].find(
    (o) => o.value === line.predocProcessInstanceId,
  )?.city;
}

function stayCapFromCity(city?: string) {
  if (!city) return 300;
  return ['北京', '上海', '广州', '深圳'].includes(city) ? 400 : 300;
}

function needOverLimitReason(line: LineRow) {
  return line.category === 'travel' && Number(line.amount) > stayCapFromCity(predocCity(line));
}

function addLine() {
  formData.value.lines.push({});
}

function removeLine(index: number) {
  if (formData.value.lines.length <= 1) {
    message.warning('至少一行明细');
    return;
  }
  formData.value.lines.splice(index, 1);
}

function lineTotal() {
  return formData.value.lines.reduce((s, l) => s + Number(l.amount || 0), 0);
}

function getPredictVariables(): Record<string, unknown> {
  return { applyAmount: lineTotal(), periodLabel: formData.value.periodLabel };
}

function feeDateStr(v: unknown) {
  if (v == null || v === '') return undefined;
  if (Array.isArray(v) && v.length >= 3) {
    return `${v[0]}-${String(v[1]).padStart(2, '0')}-${String(v[2]).padStart(2, '0')}`;
  }
  const s = String(v);
  return s.length >= 10 ? s.slice(0, 10) : s;
}

async function reset(opts?: { copyFromBusinessKey?: string }) {
  formData.value = {
    periodLabel: dayjs().format('YYYY-MM'),
    payeeAccountName: '',
    payeeBankName: '',
    payeeAccountNo: '',
    extraAttachments: [],
    lines: [{}],
  };
  applyLoginUser();
  const copyId = Number(opts?.copyFromBusinessKey);
  if (Number.isFinite(copyId) && copyId > 0) {
    try {
      const bill = await getExpenseReimbursement(copyId);
      formData.value = {
        ...formData.value,
        periodLabel: bill.periodLabel || formData.value.periodLabel,
        payeeAccountName: bill.payeeAccountName || '',
        payeeBankName: bill.payeeBankName || '',
        payeeAccountNo: bill.payeeAccountNo || '',
        extraAttachments: bill.extraAttachments || [],
        lines: (bill.lines || []).map((l) => ({
          category: l.category,
          feeDate: feeDateStr(l.feeDate),
          amount: l.amount,
          predocType: l.predocType,
          predocProcessInstanceId: l.predocProcessInstanceId,
          remark: l.remark,
          stayCityTier: l.stayCityTier,
          overLimitReason: l.overLimitReason,
        })),
      };
      if (!formData.value.lines.length) {
        formData.value.lines = [{}];
      }
    } catch {
      /* 再提带数失败仍可空白发起 */
    }
  }
  emit('predictChange', getPredictVariables());
}

const rules: Record<string, Rule[]> = {
  periodLabel: [{ required: true, message: '请填写费用归属期间', trigger: 'blur' }],
  payeeAccountName: [{ required: true, message: '请填写收款户名', trigger: 'blur' }],
  payeeBankName: [{ required: true, message: '请填写开户行', trigger: 'blur' }],
  payeeAccountNo: [{ required: true, message: '请填写收款账号', trigger: 'blur' }],
};

async function submit(ctx?: { startCompanyDeptId?: number; startDeptId?: number }): Promise<void> {
  await formRef.value?.validate();
  const lines = formData.value.lines
    .filter((l) => l.category && l.feeDate && Number(l.amount) > 0)
    .map((l) => ({
      lineKind: 'NORMAL' as const,
      category: String(l.category),
      feeDate: String(l.feeDate),
      amount: Number(l.amount),
      predocType:
        l.category === 'travel'
          ? 'TRIP'
          : l.category === 'transport'
            ? l.predocType || 'TRIP'
            : undefined,
      predocProcessInstanceId: l.predocProcessInstanceId,
      remark: l.remark,
      stayCityTier: l.category === 'travel' ? l.stayCityTier : undefined,
      overLimitReason: l.category === 'travel' ? l.overLimitReason : undefined,
    }));
  if (lines.length === 0) {
    message.warning('请至少填写一行完整明细');
    throw new Error('empty lines');
  }
  const stayMissing = formData.value.lines.find(
    (l) => l.category === 'travel' && Number(l.amount) > 0 && !predocCity(l),
  );
  if (stayMissing) {
    message.warning('差旅请选择带城市的出差/外出单');
    throw new Error('stay tier');
  }
  const over = formData.value.lines.find(
    (l) => needOverLimitReason(l) && !String(l.overLimitReason || '').trim(),
  );
  if (over) {
    message.warning('住宿超标请填写超标原因（不拦金额）');
    throw new Error('over limit reason');
  }
  submitting.value = true;
  try {
    await createNoInvoiceExpense({
      periodLabel: String(formData.value.periodLabel),
      proxyTicket: false,
      actualUserId: formData.value.actualUserId,
      payeeAccountName: String(formData.value.payeeAccountName),
      payeeBankName: String(formData.value.payeeBankName),
      payeeAccountNo: String(formData.value.payeeAccountNo),
      extraAttachments: formData.value.extraAttachments || [],
      lines,
      startCompanyDeptId: ctx?.startCompanyDeptId,
      startDeptId: ctx?.startDeptId,
    } as any);
    message.success('提交成功');
    emit('success');
  } finally {
    submitting.value = false;
  }
}

applyLoginUser();
onMounted(async () => {
  await loadPredocOptions();
  try {
    const [users, depts] = await Promise.all([getSimpleUserList(), getSimpleDeptList()]);
    const dmap: Record<number, { name?: string; parentId?: number; orgType?: string }> = {};
    for (const d of depts || []) {
      if (d.id != null) dmap[Number(d.id)] = d;
    }
    deptById.value = dmap;
    userOptionsAll.value = (users || []).map((u) => ({
      label: u.nickname || String(u.id),
      value: Number(u.id),
      deptId: u.deptId,
      deptName: (u as any).deptName,
    }));
    onActualUserChange(formData.value.actualUserId);
  } catch {
    message.warning('人员列表加载失败，仅可报自己');
  }
});
defineExpose({ reset, submit, getPredictVariables, submitting });
</script>

<template>
  <Form
    ref="formRef"
    :model="formData"
    :rules="rules"
    :label-col="{ span: 5 }"
    :wrapper-col="{ span: 18 }"
  >
    <Form.Item label="申请人">
      <Input :value="formData.userNickname" disabled />
    </Form.Item>
    <Form.Item label="实际报销人">
      <Select
        v-model:value="formData.actualUserId"
        class="w-full"
        show-search
        option-filter-prop="label"
        :options="userOptionsAll"
        placeholder="默认申请人，可改为被报销人"
        @change="onActualUserChange"
      />
    </Form.Item>
    <Form.Item label="主体公司">
      <Input :value="formData.entityCompanyName" disabled />
    </Form.Item>
    <Form.Item label="部门">
      <Input :value="formData.deptName" disabled />
    </Form.Item>
    <Form.Item label="费用归属期间" name="periodLabel">
      <Input v-model:value="formData.periodLabel" placeholder="YYYY-MM" />
    </Form.Item>
    <Form.Item label="收款户名" name="payeeAccountName">
      <Input v-model:value="formData.payeeAccountName" placeholder="员工卡户名" />
    </Form.Item>
    <Form.Item label="开户行" name="payeeBankName">
      <Input v-model:value="formData.payeeBankName" placeholder="工资开户行" />
    </Form.Item>
    <Form.Item label="收款账号" name="payeeAccountNo">
      <Input v-model:value="formData.payeeAccountNo" placeholder="员工卡账号" />
    </Form.Item>
    <Form.Item label="无票明细">
      <div
        v-for="(line, index) in formData.lines"
        :key="index"
        class="mb-2 flex flex-wrap items-center gap-2"
      >
        <Select
          v-model:value="line.category"
          class="w-28"
          :options="categoryOptions"
          placeholder="分类"
          @change="onCategoryChange(index)"
        />
        <Select
          v-if="needsPredoc(line)"
          :value="line.predocProcessInstanceId"
          class="w-56"
          :options="predocOptions(line.category)"
          placeholder="先选已通过出差/外出"
          allow-clear
          @change="(v) => onPredocChange(index, v as string)"
        />
        <Button
          v-if="line.predocProcessInstanceId"
          type="link"
          class="px-1"
          @click="openPredoc(line)"
        >
          {{ predocLinkLabel(line) }}
        </Button>
        <template v-if="lineDetailsEnabled(line)">
          <DatePicker
            :value="line.feeDate ? dayjs(line.feeDate) : undefined"
            class="w-36"
            @change="(d) => (line.feeDate = d ? dayjs(d).format('YYYY-MM-DD') : undefined)"
          />
          <InputNumber v-model:value="line.amount" :min="0.01" :precision="2" placeholder="金额" />
          <Input
            v-if="line.category === 'travel' && needOverLimitReason(line)"
            v-model:value="line.overLimitReason"
            class="w-48"
            placeholder="超标原因"
          />
          <Input v-model:value="line.remark" class="w-36" placeholder="说明" />
        </template>
        <span v-else-if="needsPredoc(line)" class="text-xs text-gray-500">请先选择关联单</span>
        <Button danger size="small" @click="removeLine(index)">删</Button>
      </div>
      <Button size="small" @click="addLine">加一行</Button>
      <div class="mt-1 text-xs text-gray-500">合计 {{ lineTotal().toFixed(2) }}</div>
      <div
        v-if="formData.lines.some((l) => l.category === 'travel')"
        class="mt-1 text-xs text-amber-700"
      >
        住宿标准按出差/外出城市裁定：北上广深 400 元/晚，其他 300。超标不拦提单，须填超标原因。
      </div>
    </Form.Item>
    <Form.Item label="其他附件">
      <FileUpload
        :value="formData.extraAttachments || []"
        :max-number="30"
        :multiple="true"
        :max-size="20"
        help-text="行程单等其他材料，最多 30 个"
        @update:value="
          (v) =>
            (formData.extraAttachments = Array.isArray(v)
              ? v.filter(Boolean)
              : v
                ? [String(v)]
                : [])
        "
      />
    </Form.Item>
  </Form>
  <PredocOverlay
    v-model:open="predocOpen"
    :predoc-type="overlayType"
    :bill-id="overlayBillId"
  />
</template>
