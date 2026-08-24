import { formatDate, formatDateTime } from '@vben/utils';

export function displayDate(val: unknown) {
  if (val == null || val === '') return '-';
  return (formatDate(val as any) as string) || '-';
}

export function displayDateTime(val: unknown) {
  if (val == null || val === '') return '-';
  return (formatDateTime(val as any) as string) || '-';
}
