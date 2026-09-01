import { DICT_TYPE } from '@vben/constants';
import { getDictLabel } from '@vben/hooks';

const STATUS: Record<string, string> = {
  PENDING: '审批中',
  WAIT_PAY: '已通过 · 待支付',
  PAID: '已支付',
  REJECTED: '已驳回',
  CANCELLED: '已取消',
};

const TIMING: Record<string, string> = {
  IMMEDIATE: '即时',
  MONTH_END: '月底',
  ON_NOTICE: '通知',
};

const REASON: Record<string, string> = {
  BUSINESS: '商务',
  PURCHASE: '采购',
  LEASE: '租赁',
  SALARY: '薪资',
  TAX: '税金',
};

function pick(map: Record<string, string>, value?: null | string) {
  const raw = String(value || '').trim();
  if (!raw) return '-';
  return map[raw] || raw;
}

export function financeStatusLabel(value?: null | string) {
  return pick(STATUS, value);
}

export function financeTimingLabel(value?: null | string) {
  const raw = String(value || '').trim();
  if (!raw) return '-';
  return getDictLabel('finance_payment_timing', raw) || TIMING[raw] || raw;
}

export function financeReasonLabel(value?: null | string) {
  const raw = String(value || '').trim();
  if (!raw) return '-';
  return getDictLabel('finance_payment_reason', raw) || REASON[raw] || raw;
}

export function financeProductLabel(value?: null | string) {
  const raw = String(value || '').trim();
  if (!raw) return '-';
  return getDictLabel(DICT_TYPE.FINANCE_PRODUCT_TYPE, raw) || raw;
}
