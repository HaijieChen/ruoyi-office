import type { BpmOAOvertimeCalendarApi } from '#/api/bpm/oa/overtime-calendar';

export type CalendarDayKind =
  | 'LEGAL_HOLIDAY'
  | 'WEEKEND'
  | 'MAKEUP_WORKDAY'
  | 'MAKEUP_REST'
  | 'WEEKDAY';

export interface CalendarDayRow {
  date: string;
  weekday: string;
  festival: string;
  kind: CalendarDayKind;
  kindLabel: string;
  allowed: boolean;
}

const WEEKDAYS = ['日', '一', '二', '三', '四', '五', '六'];

const KIND_LABEL: Record<CalendarDayKind, string> = {
  LEGAL_HOLIDAY: '法定节假日',
  WEEKEND: '普通周末',
  MAKEUP_WORKDAY: '调休上班',
  MAKEUP_REST: '调休休息',
  WEEKDAY: '普通工作日',
};

/** Solar statutory names from 国令第795号; lunar festivals use that year's legal set. */

export function parseJsonList(json?: string | null): string[] {
  if (!json) {
    return [];
  }
  try {
    const value = JSON.parse(json) as unknown;
    return Array.isArray(value)
      ? value.filter((item): item is string => typeof item === 'string')
      : [];
  } catch {
    return [];
  }
}

export function weekdayLabel(iso: string): string {
  const [year, month, day] = iso.split('-').map(Number);
  if (!year || !month || !day) {
    return '';
  }
  return WEEKDAYS[new Date(year, month - 1, day).getDay()] ?? '';
}

export function parseJsonMap(json?: string | null): Record<string, string> {
  if (!json) {
    return {};
  }
  try {
    const value = JSON.parse(json) as unknown;
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
      return {};
    }
    const out: Record<string, string> = {};
    for (const [key, item] of Object.entries(value as Record<string, unknown>)) {
      if (typeof item === 'string' && item) {
        out[key] = item;
      }
    }
    return out;
  } catch {
    return {};
  }
}

export function festivalName(iso: string, festivals: Record<string, string> = {}): string {
  return festivals[iso] || '';
}

export function classifyDate(
  iso: string,
  legal: Set<string>,
  work: Set<string>,
  rest: Set<string>,
): CalendarDayKind {
  if (legal.has(iso)) {
    return 'LEGAL_HOLIDAY';
  }
  if (work.has(iso)) {
    return 'MAKEUP_WORKDAY';
  }
  if (rest.has(iso)) {
    return 'MAKEUP_REST';
  }
  const [year, month, day] = iso.split('-').map(Number);
  const dow = new Date(year, month - 1, day).getDay();
  if (dow === 0 || dow === 6) {
    return 'WEEKEND';
  }
  return 'WEEKDAY';
}

function isoFromUtc(year: number, monthIndex: number, day: number): string {
  const month = String(monthIndex + 1).padStart(2, '0');
  const dd = String(day).padStart(2, '0');
  return `${year}-${month}-${dd}`;
}

export function daysInYear(year: number): string[] {
  const dates: string[] = [];
  for (let month = 0; month < 12; month += 1) {
    const count = new Date(year, month + 1, 0).getDate();
    for (let day = 1; day <= count; day += 1) {
      dates.push(isoFromUtc(year, month, day));
    }
  }
  return dates;
}

export function kindLabel(kind: CalendarDayKind): string {
  return KIND_LABEL[kind];
}

export function isSeedVersion(row: BpmOAOvertimeCalendarApi.Version): boolean {
  return (row.contentHash || '').startsWith('seed-')
    || (row.parseNote || '').includes('初始化');
}

export function statusLabel(status?: string): string {
  switch (status) {
    case 'ACTIVE':
      return '已生效';
    case 'PENDING':
      return '待核验';
    case 'FAILED':
      return '采集失败';
    case 'NOT_PUBLISHED':
      return '截至本次查询未检索到该年度公告';
    case 'REJECTED':
      return '已拒绝';
    case 'SUPERSEDED':
      return '已被替代';
    default:
      return status || '未知';
  }
}

export function buildDayRows(
  version: BpmOAOvertimeCalendarApi.Version,
  year = version.calendarYear,
): CalendarDayRow[] {
  const legal = new Set(parseJsonList(version.legalHolidaysJson));
  const work = new Set(parseJsonList(version.makeupWorkdaysJson));
  const rest = new Set(parseJsonList(version.makeupRestDaysJson));
  const dates = year
    ? daysInYear(year)
    : [...new Set([...legal, ...work, ...rest, ...parseJsonList(version.weekendsJson)])].sort();
  return dates.map((date) => {
    const kind = classifyDate(date, legal, work, rest);
    return {
      date,
      weekday: weekdayLabel(date),
      festival: festivalName(date, parseJsonMap(version.festivalsJson)),
      kind,
      kindLabel: kindLabel(kind),
      allowed: kind === 'LEGAL_HOLIDAY' || kind === 'WEEKEND',
    };
  });
}
