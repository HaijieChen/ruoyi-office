function pad2(n: number) {
  return String(n).padStart(2, '0');
}

function toDateStr(value: unknown): string | undefined {
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

/** 日期 + 时刻 → 本地时间戳（毫秒） */
export function combineDateAndTime(
  date?: unknown,
  clock?: unknown,
): number | undefined {
  const dateStr = toDateStr(date);
  const clockStr = toClockStr(clock);
  if (!dateStr || !clockStr) {
    return undefined;
  }
  const ms = new Date(`${dateStr}T${clockStr}`).getTime();
  return Number.isFinite(ms) ? ms : undefined;
}

function isSameLocalDay(startMs: number, endMs: number): boolean {
  const start = new Date(startMs);
  const end = new Date(endMs);
  return (
    start.getFullYear() === end.getFullYear() &&
    start.getMonth() === end.getMonth() &&
    start.getDate() === end.getDate()
  );
}

/**
 * 展示用时长：同一自然日、结束晚于开始；分钟/60 HALF_UP 一位小数后与 8 取小。
 * 不足 2 小时仍返回时钟值，便于表单随时间变化。
 */
export function previewOvertimeHours(
  startTime?: number | string | null,
  endTime?: number | string | null,
): number | undefined {
  if (
    startTime == null ||
    startTime === '' ||
    endTime == null ||
    endTime === ''
  ) {
    return undefined;
  }
  const start = Number(startTime);
  const end = Number(endTime);
  if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start) {
    return undefined;
  }
  if (!isSameLocalDay(start, end)) {
    return undefined;
  }
  const minutes = Math.floor((end - start) / 60_000);
  if (minutes <= 0) {
    return undefined;
  }
  const hours = Math.round((minutes * 10) / 60) / 10;
  if (hours <= 0) {
    return undefined;
  }
  return hours > 8 ? 8 : hours;
}

/**
 * 与后端 OaOvertimeHours 对齐：跨日 / 不足 2 小时返回 undefined；否则 min(时钟, 8) 一位小数。
 */
export function calcOvertimeHours(
  startTime?: number | string | null,
  endTime?: number | string | null,
): number | undefined {
  const hours = previewOvertimeHours(startTime, endTime);
  if (hours == null || hours < 2) {
    return undefined;
  }
  return hours;
}

export function getOvertimeRangeError(
  startTime?: number | string | null,
  endTime?: number | string | null,
): string | undefined {
  if (
    startTime == null ||
    startTime === '' ||
    endTime == null ||
    endTime === ''
  ) {
    return undefined;
  }
  const start = Number(startTime);
  const end = Number(endTime);
  if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start) {
    return '结束时间必须晚于开始时间';
  }
  if (!isSameLocalDay(start, end)) {
    return '开始与结束必须为同一天';
  }
  const preview = previewOvertimeHours(start, end);
  if (preview == null) {
    return '结束时间必须晚于开始时间';
  }
  if (preview < 2) {
    return '加班时长至少 2 小时';
  }
  return undefined;
}
