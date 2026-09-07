function pad2(n: number) {
  return String(n).padStart(2, '0');
}

/** 日期控件 / ISO 字符串 → YYYY-MM-DD；无法解析则 undefined */
export function toPunchDateStr(value: unknown): string | undefined {
  if (value == null || value === '') {
    return undefined;
  }
  if (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as { format?: unknown }).format === 'function'
  ) {
    return (value as { format: (p: string) => string }).format('YYYY-MM-DD');
  }
  if (typeof value === 'number' || value instanceof Date) {
    const d = value instanceof Date ? value : new Date(value);
    if (Number.isNaN(d.getTime())) {
      return undefined;
    }
    return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
  }
  const match = String(value).match(/^(\d{4}-\d{2}-\d{2})/);
  return match?.[1];
}

function toClockStr(value: unknown): string | undefined {
  if (value == null || value === '') {
    return undefined;
  }
  if (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as { format?: unknown }).format === 'function'
  ) {
    return (value as { format: (p: string) => string }).format('HH:mm:ss');
  }
  if (typeof value === 'number' || value instanceof Date) {
    const d = value instanceof Date ? value : new Date(value);
    if (Number.isNaN(d.getTime())) {
      return undefined;
    }
    return `${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`;
  }
  const text = String(value);
  if (/^\d{2}:\d{2}$/.test(text)) {
    return `${text}:00`;
  }
  if (/^\d{2}:\d{2}:\d{2}/.test(text)) {
    return text.slice(0, 8);
  }
  return undefined;
}

/** 补卡时刻：日期 + 时刻 → 本地时间戳（毫秒） */
export function combinePunchDateAndTime(
  date?: unknown,
  clock?: unknown,
): number | undefined {
  const dateStr = toPunchDateStr(date);
  const clockStr = toClockStr(clock);
  if (!dateStr || !clockStr) {
    return undefined;
  }
  const ms = new Date(`${dateStr}T${clockStr}`).getTime();
  return Number.isFinite(ms) ? ms : undefined;
}

/**
 * 无补卡日期不展示；次数未知不编造默认 2；剩余 0 仍展示。
 */
export function remainingLabel(
  punchDate?: string | null,
  remaining?: number | null,
): string | undefined {
  if (!toPunchDateStr(punchDate)) {
    return undefined;
  }
  if (remaining == null || typeof remaining !== 'number' || Number.isNaN(remaining)) {
    return undefined;
  }
  return `本月剩余 ${remaining} 次`;
}

/** 选了日期且与上次请求不同才拉 remaining */
export function shouldFetchRemaining(
  punchDate?: string | null,
  lastFetchedDate?: string | null,
): boolean {
  const date = toPunchDateStr(punchDate);
  if (!date) {
    return false;
  }
  return date !== (lastFetchedDate || undefined);
}
