/** Approval-only field view: wrap long text, never rely on disabled 4-column inputs. */

export type SealApprovalField = {
  key: string;
  label: string;
  value: string;
  empty: boolean;
  fullWidth?: boolean;
};

function text(value: unknown): string {
  if (value == null || value === '') return '';
  return String(value);
}

function dictLabel(
  options: Array<{ label?: string; value?: unknown }> | undefined,
  value: unknown,
): string {
  if (value == null || value === '') return '';
  const hit = options?.find((item) => String(item.value) === String(value));
  return hit?.label ? String(hit.label) : String(value);
}

function formatTime(value: unknown): string {
  if (value == null || value === '') return '';
  let raw: unknown = value;
  if (Array.isArray(value)) {
    raw = value[0];
  }
  if (typeof raw === 'number' || (typeof raw === 'string' && /^\d+$/.test(raw))) {
    const d = new Date(Number(raw));
    return formatDate(d);
  }
  if (typeof raw === 'string') {
    const normalized = raw.includes('T') ? raw.replace('T', ' ').replace(/\.\d+Z?$/, '') : raw;
    const d = new Date(raw);
    if (!Number.isNaN(d.getTime()) && /^\d{4}-\d{2}-\d{2}/.test(raw)) {
      return formatDate(d);
    }
    return normalized;
  }
  if (raw instanceof Date) {
    return formatDate(raw);
  }
  return String(raw);
}

function formatDate(d: Date): string {
  if (Number.isNaN(d.getTime())) return '';
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

export function mergeSealSavePayload(
  formData: Record<string, unknown>,
  formValues: Record<string, unknown> | null | undefined,
  keepActualReturnFromFormData: boolean,
): Record<string, unknown> {
  const data = { ...formData, ...(formValues || {}) };
  if (keepActualReturnFromFormData) {
    data.actualReturnTime = formData.actualReturnTime;
  }
  return data;
}

export function buildSealApprovalFields(input: {
  sealName?: unknown;
  useType?: unknown;
  useMode?: unknown;
  documentTitle?: unknown;
  documentType?: unknown;
  documentCount?: unknown;
  contractAmount?: unknown;
  contractParty?: unknown;
  expectedUseTime?: unknown;
  expectedReturnTime?: unknown;
  actualReturnTime?: unknown;
  isUrgent?: unknown;
  cause?: unknown;
  remark?: unknown;
  useTypeOptions?: Array<{ label?: string; value?: unknown }>;
  useModeOptions?: Array<{ label?: string; value?: unknown }>;
  urgentOptions?: Array<{ label?: string; value?: unknown }>;
}): SealApprovalField[] {
  const showContract = Number(input.useType) === 1;
  const rows: SealApprovalField[] = [
    { key: 'sealName', label: '印章', value: text(input.sealName), empty: !text(input.sealName) },
    { key: 'useType', label: '用章类型', value: dictLabel(input.useTypeOptions, input.useType), empty: input.useType == null || input.useType === '' },
    { key: 'useMode', label: '用章方式', value: dictLabel(input.useModeOptions, input.useMode), empty: input.useMode == null || input.useMode === '' },
    { key: 'documentTitle', label: '文件标题', value: text(input.documentTitle), empty: !text(input.documentTitle) },
    { key: 'documentType', label: '文件类型', value: text(input.documentType), empty: !text(input.documentType) },
    { key: 'documentCount', label: '文件份数', value: input.documentCount == null || input.documentCount === '' ? '' : String(input.documentCount), empty: input.documentCount == null || input.documentCount === '' },
  ];
  if (showContract) {
    rows.push(
      { key: 'contractAmount', label: '合同金额', value: text(input.contractAmount), empty: input.contractAmount == null || input.contractAmount === '' },
      { key: 'contractParty', label: '合同对方', value: text(input.contractParty), empty: !text(input.contractParty) },
    );
  }
  rows.push(
    { key: 'expectedUseTime', label: '使用日期', value: formatTime(input.expectedUseTime), empty: !formatTime(input.expectedUseTime) },
    { key: 'expectedReturnTime', label: '预计归还日期', value: formatTime(input.expectedReturnTime), empty: !formatTime(input.expectedReturnTime) },
    { key: 'actualReturnTime', label: '实际归还时间', value: formatTime(input.actualReturnTime), empty: !formatTime(input.actualReturnTime) },
    { key: 'isUrgent', label: '是否紧急', value: dictLabel(input.urgentOptions, input.isUrgent), empty: input.isUrgent == null || input.isUrgent === '' },
    { key: 'cause', label: '用章事由', value: text(input.cause), empty: !text(input.cause), fullWidth: true },
    { key: 'remark', label: '备注', value: text(input.remark), empty: !text(input.remark), fullWidth: true },
  );
  return rows;
}
