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
  Switch,
  message,
} from 'ant-design-vue';
import dayjs from 'dayjs';

import { getOutingPage } from '#/api/bpm/oa/outing';
import { getTripPage } from '#/api/bpm/oa/trip';
import { getSimpleUserList } from '#/api/system/user';
import { getSimpleDeptList } from '#/api/system/dept';
import { getEmployeeWageCardByUserId } from '#/api/hrm/employee';
import {
  createExpenseReimbursement,
  ocrExpenseInvoice,
} from '#/api/finance/expense-reimbursement';
import { mapWageCardToPayee } from '../payee-prefill';
import { FileUpload } from '#/components/upload';
import { useUpload } from '#/components/upload/use-upload';
import PredocOverlay from './predoc-overlay.vue';

defineOptions({ name: 'FinanceExpenseReimbursementFormBody' });

const emit = defineEmits<{
  predictChange: [vars: Record<string, unknown>];
  success: [];
}>();

const userStore = useUserStore();
const formRef = ref();
const submitting = ref(false);
const { httpRequest } = useUpload();

interface LineRow {
  lineKind: 'NORMAL' | 'PROXY';
  category?: string;
  feeDate?: string;
  amount?: number;
  taxAmount?: number;
  invoiceFileUrl?: string;
  invoiceNo?: string;
  predocType?: string;
  predocProcessInstanceId?: string;
  predocBillId?: number;
  remark?: string;
  stayCityTier?: string;
  overLimitReason?: string;
  invoiceType?: string;
  subItem?: string;
  _uploading?: boolean;
  _uploadEpoch?: number;
  /** 同组多票拆行：继承费用类型/子项目/前置单，不再展示这些下拉 */
  _sibling?: boolean;
}

const formData = ref<{
  userNickname?: string;
  deptName?: string;
  entityCompanyName?: string;
  actualUserId?: number;
  periodLabel?: string;
  proxyTicket: boolean;
  payeeAccountName?: string;
  payeeBankName?: string;
  payeeAccountNo?: string;
  extraAttachments: string[];
  lines: LineRow[];
}>({
  proxyTicket: false,
  periodLabel: dayjs().format('YYYY-MM'),
  extraAttachments: [],
  lines: [{}],
});

const tripOptions = ref<
  {
    label: string;
    value: string;
    type: 'TRIP';
    billId?: number;
    city?: string;
    startTime?: number;
    endTime?: number;
    userId?: number;
    companionUserIds?: number[];
  }[]
>([]);
const outingOptions = ref<
  {
    label: string;
    value: string;
    type: 'OUTING';
    billId?: number;
    city?: string;
    startTime?: number;
    endTime?: number;
    userId?: number;
  }[]
>([]);
const userSex = ref<Record<number, number>>({});
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

const invoiceTypeOptions = [
  { label: '专票', value: '专票' },
  { label: '普票', value: '普票' },
  { label: '其他', value: '其他' },
];
function subItemOptions(category?: string) {
  return getDictOptions('finance_expense_subitem', 'string')
    .filter((d) => !category || String(d.value).startsWith(String(category) + '.'))
    .map((d) => ({ label: d.label, value: String(d.value) }));
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

function expectedKind(): 'NORMAL' | 'PROXY' {
  return formData.value.proxyTicket ? 'PROXY' : 'NORMAL';
}

function onProxyChange(checked: boolean) {
  formData.value.proxyTicket = checked;
  formData.value.lines = [{ lineKind: expectedKind() }];
  emit('predictChange', getPredictVariables());
}

async function loadPredocOptions() {
  try {
    const [trips, outings] = await Promise.all([
      getTripPage({ pageNo: 1, pageSize: 50 }),
      getOutingPage({ pageNo: 1, pageSize: 50 }),
    ]);
    tripOptions.value = (trips?.list || [])
      .filter((t) => Number(t.status) === 2 && t.processInstanceId)
      .map((t) => ({
        value: String(t.processInstanceId),
        type: 'TRIP' as const,
        billId: t.id,
        city: t.destination,
        startTime: t.startTime,
        endTime: t.endTime,
        userId: t.userId,
        companionUserIds: t.companionUserIds || (t.companionUserId ? [t.companionUserId] : []),
        label: `出差#${t.id} ${t.destination || ''}`.trim(),
      }));
    outingOptions.value = (outings?.list || [])
      .filter((t) => Number(t.status) === 2 && t.processInstanceId)
      .map((t) => ({
        value: String(t.processInstanceId),
        type: 'OUTING' as const,
        billId: t.id,
        city: t.location,
        startTime: t.startTime,
        endTime: t.endTime,
        userId: t.userId,
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

function applyToSiblings(index: number, patch: (line: LineRow) => void) {
  const lines = formData.value.lines;
  for (let i = index + 1; i < lines.length; i++) {
    const next = lines[i];
    if (!next?._sibling) break;
    patch(next);
  }
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
  applyToSiblings(index, (s) => {
    s.category = line.category;
    s.subItem = line.subItem;
    s.predocProcessInstanceId = undefined;
    s.predocType = undefined;
    s.stayCityTier = undefined;
    s.overLimitReason = undefined;
  });
}

function onSubItemChange(index: number) {
  const line = formData.value.lines[index];
  if (!line) return;
  applyToSiblings(index, (s) => {
    s.subItem = line.subItem;
  });
}

function predocOptions(category?: string) {
  if (category === 'travel') return tripOptions.value;
  if (category === 'transport') return [...tripOptions.value, ...outingOptions.value];
  return [];
}

function normalizeCompanyName(name?: string) {
  return String(name || '')
    .replace(/\s+/g, '')
    .replace(/[（）()]/g, '')
    .replace(/有限责任公司|股份有限公司|有限公司/g, '');
}

function buyerMatchesEntity(buyer?: string, entity?: string) {
  const a = normalizeCompanyName(buyer);
  const b = normalizeCompanyName(entity);
  if (!a || !b) return true;
  return a.includes(b) || b.includes(a);
}

async function runInvoiceOcr(index: number, url: string, file?: File) {
  const line = formData.value.lines[index];
  if (!line) return;
  const hide = message.loading({ content: '正在识别发票...', duration: 0 });
  try {
    const ocr = await ocrExpenseInvoice(url, file);
    if (ocr?.used || formData.value.lines.some((l, i) => i !== index && l.invoiceNo && l.invoiceNo === ocr?.invoiceNo)) {
      message.error(`发票 ${ocr.invoiceNo || ''} 已被使用`);
      throw new Error('invoice used');
    }
    if (!buyerMatchesEntity(ocr?.buyerName, formData.value.entityCompanyName)) {
      message.error('与报销主体不匹配，请重新上传发票');
      throw new Error('buyer mismatch');
    }
    if (ocr?.invoiceNo) line.invoiceNo = String(ocr.invoiceNo);
    if (ocr?.feeDate) line.feeDate = String(ocr.feeDate).slice(0, 10);
    if (ocr?.amount != null) line.amount = Number(ocr.amount);
    if (ocr?.taxAmount != null) line.taxAmount = Number(ocr.taxAmount);
    line.invoiceType = ocr?.invoiceType || '其他';
    if (ocr?.invoiceNo || ocr?.feeDate || ocr?.amount != null || ocr?.taxAmount != null) {
      message.success('已识别票号/日期/金额/税额/票种，请核对，可修改');
    } else {
      message.warning('未识别到票号/日期/金额，请手填');
    }
  } catch (e: any) {
    if (
      String(e?.message || '').includes('invoice used') ||
      String(e?.message || '').includes('buyer mismatch')
    ) {
      throw e;
    }
    message.warning('识别失败，请手填日期和金额');
  } finally {
    hide();
  }
}

function rawUploadFile(file: File) {
  const inner = (file as any)?.originFileObj;
  return inner instanceof Blob ? inner : file;
}

let invoiceUploadChain = Promise.resolve();

function claimOrCloneInvoiceLine(sourceIndex: number): number {
  const lines = formData.value.lines;
  const source = lines[sourceIndex];
  if (!source) return sourceIndex;
  if (!source.invoiceFileUrl && !source._uploading) {
    source._uploading = true;
    return sourceIndex;
  }
  const cloned: LineRow = {
    lineKind: source.lineKind,
    category: source.category,
    subItem: source.subItem,
    predocType: source.predocType,
    predocProcessInstanceId: source.predocProcessInstanceId,
    stayCityTier: source.stayCityTier,
    _uploading: true,
    _sibling: true,
  };
  let insertAt = sourceIndex + 1;
  while (insertAt < lines.length && lines[insertAt]?._uploading) {
    insertAt++;
  }
  lines.splice(insertAt, 0, cloned);
  return insertAt;
}

async function uploadInvoice(index: number, file: File, onUploadProgress?: any) {
  const raw = rawUploadFile(file);
  const run = invoiceUploadChain.then(async () => {
    const target = claimOrCloneInvoiceLine(index);
    try {
      if (raw instanceof Blob) {
        await runInvoiceOcr(target, '', raw as File);
      }
      const res = await httpRequest(raw, onUploadProgress);
      const url =
        typeof res === 'string'
          ? res
          : String((res as any)?.url || (res as any)?.data || '');
      const line = formData.value.lines[target];
      if (line) {
        line.invoiceFileUrl = url || line.invoiceFileUrl;
        line._uploading = false;
        line._uploadEpoch = (line._uploadEpoch || 0) + 1;
      }
      return res;
    } catch (e) {
      const line = formData.value.lines[target];
      if (line) {
        line._uploading = false;
        line.invoiceFileUrl = undefined;
        if (target !== index) {
          formData.value.lines.splice(target, 1);
        } else {
          line._uploadEpoch = (line._uploadEpoch || 0) + 1;
        }
      }
      throw e;
    }
  });
  invoiceUploadChain = run.then(
    () => undefined,
    () => undefined,
  );
  return run;
}

async function onInvoiceUpload(index: number, val: string | string[]) {
  const line = formData.value.lines[index];
  if (!line) return;
  const urls = (Array.isArray(val) ? val : val ? [val] : []).filter(Boolean);
  if (urls.length === 0) {
    line.invoiceFileUrl = undefined;
    return;
  }
  if (!line.invoiceFileUrl) {
    line.invoiceFileUrl = String(urls[0]);
  }
  line._uploadEpoch = (line._uploadEpoch || 0) + 1;
}

function onPredocChange(index: number, processInstanceId?: string) {
  const line = formData.value.lines[index];
  if (!line) return;
  line.predocProcessInstanceId = processInstanceId;
  const hit = [...tripOptions.value, ...outingOptions.value].find(
    (o) => o.value === processInstanceId,
  );
  line.predocType = hit?.type;
  line.predocBillId = hit?.billId;
  line.stayCityTier = ['北京', '上海', '广州', '深圳'].includes(String(hit?.city || ''))
    ? 'T1'
    : hit?.city
      ? 'OTHER'
      : undefined;
  applyToSiblings(index, (s) => {
    s.predocProcessInstanceId = line.predocProcessInstanceId;
    s.predocType = line.predocType;
    s.stayCityTier = line.stayCityTier;
  });
}

function predocCity(line: LineRow) {
  return [...tripOptions.value, ...outingOptions.value].find(
    (o) => o.value === line.predocProcessInstanceId,
  )?.city;
}

function stayRate(city?: string) {
  if (!city) return 300;
  return ['北京', '上海', '广州', '深圳'].includes(city) ? 400 : 300;
}

function roomsForSameGender(n: number) {
  if (n <= 0) return 0;
  return n % 2 === 0 ? n / 2 : (n + 1) / 2;
}

function stayRooms(userIds: number[]) {
  let male = 0;
  let female = 0;
  let unknown = 0;
  for (const id of userIds) {
    const sex = userSex.value[id];
    if (sex === 1) male += 1;
    else if (sex === 2) female += 1;
    else unknown += 1;
  }
  return Math.max(1, roomsForSameGender(male) + roomsForSameGender(female) + unknown);
}

function stayNights(start?: number, end?: number) {
  if (!start || !end) return 1;
  const days = dayjs(end).startOf('day').diff(dayjs(start).startOf('day'), 'day');
  return Math.max(1, days);
}

function stayCapForLine(line: LineRow) {
  const hit = [...tripOptions.value, ...outingOptions.value].find(
    (o) => o.value === line.predocProcessInstanceId,
  );
  const rate = stayRate(hit?.city);
  const nights = stayNights(hit?.startTime, hit?.endTime);
  const people = [hit?.userId, ...((hit as any)?.companionUserIds || [])].filter(Boolean).map(Number);
  const rooms = stayRooms(people.length ? people : [Number(userStore.userInfo?.id)]);
  return rate * rooms * nights;
}

function needOverLimitReason(line: LineRow) {
  return line.category === 'travel' && Number(line.amount) > stayCapForLine(line);
}

function addLine() {
  formData.value.lines.push({ lineKind: expectedKind() });
}

function removeLine(index: number) {
  if (formData.value.lines.length <= 1) {
    message.warning('至少一行明细');
    return;
  }
  formData.value.lines.splice(index, 1);
}

function lineTotal(): number {
  return formData.value.lines.reduce((s, l) => s + Number(l.amount || 0), 0);
}

function taxTotal(): number {
  return formData.value.lines.reduce((s, l) => s + Number(l.taxAmount || 0), 0);
}

function getPredictVariables(): Record<string, unknown> {
  return {
    applyAmount: lineTotal(),
    periodLabel: formData.value.periodLabel,
    proxyTicket: formData.value.proxyTicket,
  };
}

async function reset() {
  formData.value = {
    proxyTicket: false,
    periodLabel: dayjs().format('YYYY-MM'),
    payeeAccountName: '',
    payeeBankName: '',
    payeeAccountNo: '',
    extraAttachments: [],
    lines: [{ lineKind: 'NORMAL' }],
  };
  applyLoginUser();
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
  const kind = expectedKind();
  if (formData.value.lines.some((l) => l.category && !l.invoiceType)) {
    message.warning('请选择发票类型');
    throw new Error('invoice type');
  }
  const lines = formData.value.lines
    .filter((l) => l.category && l.feeDate && Number(l.amount) > 0)
    .map((l) => ({
      lineKind: kind,
      category: String(l.category),
      invoiceType: l.invoiceType,
      subItem: l.subItem,
      feeDate: String(l.feeDate),
      amount: Number(l.amount),
      taxAmount: l.taxAmount == null ? undefined : Number(l.taxAmount),
      invoiceFileUrl: l.invoiceFileUrl,
        invoiceNo: l.invoiceNo,
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
    throw new Error('stay city');
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
    await createExpenseReimbursement({
      periodLabel: String(formData.value.periodLabel),
      proxyTicket: formData.value.proxyTicket,
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
    const map: Record<number, number> = {};
    userOptionsAll.value = (users || []).map((u) => {
      if (u.id != null && (u as any).sex != null) {
        map[Number(u.id)] = Number((u as any).sex);
      }
      return {
        label: u.nickname || String(u.id),
        value: Number(u.id),
        deptId: u.deptId,
        deptName: (u as any).deptName,
      };
    });
    userSex.value = map;
    onActualUserChange(formData.value.actualUserId);
  } catch {
    userSex.value = {};
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
    <Form.Item label="是否代票">
      <Switch :checked="formData.proxyTicket" @change="onProxyChange" />
    </Form.Item>
    <Form.Item label="费用归属期间" name="periodLabel">
      <Input
        v-model:value="formData.periodLabel"
        placeholder="YYYY-MM"
        @change="emit('predictChange', getPredictVariables())"
      />
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
    <Form.Item label="实际费用">
      <div
        v-for="(line, index) in formData.lines"
        :key="index"
        class="mb-2 flex flex-wrap items-center gap-2"
      >
        <template v-if="!line._sibling">
          <Select
            v-model:value="line.category"
            class="w-28"
            :options="categoryOptions"
            placeholder="费用类型"
            @change="onCategoryChange(index)"
          />
          <Select
            v-if="line.category && subItemOptions(line.category).length"
            v-model:value="line.subItem"
            class="w-32"
            :options="subItemOptions(line.category)"
            placeholder="子项目"
            allow-clear
            @change="onSubItemChange(index)"
          />
          <Select
            v-if="needsPredoc(line)"
            :value="line.predocProcessInstanceId"
            class="w-56"
            :options="predocOptions(line.category)"
            placeholder="先选已通过出差/外出"
            allow-clear
            show-search
            option-filter-prop="label"
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
        </template>
        <template v-if="lineDetailsEnabled(line)">
          <FileUpload
            :key="`${index}-${line._uploadEpoch || 0}`"
            class="w-48"
            :value="line.invoiceFileUrl ? [line.invoiceFileUrl] : []"
            :max-number="20"
            :multiple="true"
            :max-size="20"
            :accept="['pdf', 'jpg', 'jpeg', 'png']"
            help-text="可一次选多张，每张拆成一行并识别"
            :api="(file, progress) => uploadInvoice(index, file as File, progress)"
            @update:value="(v) => onInvoiceUpload(index, v)"
          />
          <Select
            v-model:value="line.invoiceType"
            class="w-28"
            :options="invoiceTypeOptions"
            placeholder="发票类型"
          />
          <DatePicker
            :value="line.feeDate ? dayjs(line.feeDate) : undefined"
            class="w-36"
            @change="(d) => (line.feeDate = d ? dayjs(d).format('YYYY-MM-DD') : undefined)"
          />
          <InputNumber v-model:value="line.amount" :min="0.01" :precision="2" placeholder="金额" />
          <InputNumber
            v-model:value="line.taxAmount"
            :min="0"
            :precision="2"
            placeholder="专票税额"
          />
          <Input
            v-if="line.category === 'travel' && needOverLimitReason(line)"
            v-model:value="line.overLimitReason"
            class="w-48"
            placeholder="超标原因"
          />
          <Input
            v-model:value="line.invoiceNo"
            class="w-40"
            placeholder="票号"
          />
          <Input v-model:value="line.remark" class="w-36" placeholder="说明" />
        </template>
        <span v-else-if="needsPredoc(line)" class="text-xs text-gray-500">请先选择关联单</span>
        <Button danger size="small" @click="removeLine(index)">删</Button>
      </div>
      <Button size="small" @click="addLine">加一行</Button>
      <div class="mt-1 text-xs text-gray-500">
        合计金额 {{ lineTotal().toFixed(2) }}　专票税额 {{ taxTotal().toFixed(2) }}
      </div>
      <div
        v-if="formData.lines.some((l) => l.category === 'travel')"
        class="mt-1 text-xs text-amber-700"
      >
        住宿上限＝同性房间数×城市标准×天数（结束日-开始日，至少 1 天）。男女分开算房间；奇数 (n+1)/2 间、偶数 n/2 间。北上广深 400、其他 300。超标须填原因，不拦金额。
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
