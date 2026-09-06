import {
  CALENDAR_MISSING_ERROR,
  classifyOvertimeDay,
  overtimeDayAllowed,
} from './overtime-calendar';

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

export type OvertimeDaySlice = {
  day: string;
  hours: number;
  startMs: number;
  endMs: number;
};

const MIN_MINUTES = 120;

function dayIso(ms: number): string {
  const d = new Date(ms);
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
}

function nextMidnightMs(ms: number): number {
  const d = new Date(ms);
  return new Date(d.getFullYear(), d.getMonth(), d.getDate() + 1).getTime();
}

export function splitOvertimeSlices(
  startMs: number,
  endMs: number,
): OvertimeDaySlice[] {
  if (!Number.isFinite(startMs) || !Number.isFinite(endMs) || endMs <= startMs) {
    return [];
  }
  const slices: OvertimeDaySlice[] = [];
  let cursor = startMs;
  while (cursor < endMs) {
    const sliceEnd = Math.min(endMs, nextMidnightMs(cursor));
    if (sliceEnd > cursor) {
      const minutes = Math.floor((sliceEnd - cursor) / 60_000);
      if (minutes > 0) {
        let hours = Math.round((minutes * 10) / 60) / 10;
        if (hours > 0) {
          if (hours > 8) {
            hours = 8;
          }
          slices.push({
            day: dayIso(cursor),
            hours,
            startMs: cursor,
            endMs: sliceEnd,
          });
        }
      }
    }
    cursor = sliceEnd;
  }
  return slices;
}

function sumSliceHours(slices: OvertimeDaySlice[]): number {
  return Math.round(slices.reduce((acc, s) => acc + s.hours, 0) * 10) / 10;
}

/**
 * 展示用时长：按自然日拆分后各日封顶 8，合计一位小数。不足 2 小时仍返回便于随填。
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
  const slices = splitOvertimeSlices(start, end);
  if (slices.length === 0) {
    return undefined;
  }
  return sumSliceHours(slices);
}

/**
 * 与后端 OaOvertimeHours 对齐：原始分钟不足 120 返回 undefined。
 */
export function calcOvertimeHours(
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
  const minutes = Math.floor((end - start) / 60_000);
  if (minutes < MIN_MINUTES) {
    return undefined;
  }
  const hours = previewOvertimeHours(start, end);
  return hours;
}

export function getOvertimeRangeError(
  startTime?: number | string | null,
  endTime?: number | string | null,
  holiday?: string | null,
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
  const slices = splitOvertimeSlices(start, end);
  if (slices.length === 0) {
    return '结束时间必须晚于开始时间';
  }
  const days = slices.map((s) => s.day);
  try {
    const forbidden = days.filter((day) => {
      const kind = classifyOvertimeDay(day);
      return !overtimeDayAllowed(kind);
    });
    if (forbidden.length) {
      return `以下日期不是周末或法定节假日，不能申请加班：${forbidden.join('、')}`;
    }
    if (holiday === 'true' || holiday === 'false') {
      const wantLegal = holiday === 'true';
      const mismatch = days.some((day) => {
        const kind = classifyOvertimeDay(day);
        return wantLegal
          ? kind !== 'LEGAL_HOLIDAY'
          : kind !== 'WEEKEND';
      });
      if (mismatch) {
        return '加班类型与日期不一致：法定节假日请选「是」，普通周末请选「否」';
      }
    }
  } catch {
    return CALENDAR_MISSING_ERROR;
  }
  const minutes = Math.floor((end - start) / 60_000);
  if (minutes < MIN_MINUTES) {
    return '加班时长不能少于 2 小时';
  }
  return undefined;
}

export function overtimeHoursField(
  startTime?: number | string | null,
  endTime?: number | string | null,
  holiday?: string | null,
): { hours?: number; error?: string } {
  return {
    hours: previewOvertimeHours(startTime, endTime),
    error: getOvertimeRangeError(startTime, endTime, holiday),
  };
}

export function splitDateAndTime(
  value?: number | string | null,
): { date: string; clock: string } | undefined {
  if (value == null || value === '') {
    return undefined;
  }
  if (typeof value === 'string') {
    const iso = value.match(/^(\d{4}-\d{2}-\d{2})[ T](\d{2}:\d{2})/);
    if (iso) {
      return { date: iso[1], clock: iso[2] };
    }
  }
  const ms = typeof value === 'number' ? value : Number(value);
  if (!Number.isFinite(ms)) {
    return undefined;
  }
  const d = new Date(ms);
  if (Number.isNaN(d.getTime())) {
    return undefined;
  }
  return {
    date: `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`,
    clock: `${pad2(d.getHours())}:${pad2(d.getMinutes())}`,
  };
}
