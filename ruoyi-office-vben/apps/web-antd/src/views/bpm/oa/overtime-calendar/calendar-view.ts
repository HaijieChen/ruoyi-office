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

const FESTIVAL_BY_MD: Record<string, string> = {
  '01-01': '元旦',
  '02-16': '春节',
  '02-17': '春节',
  '02-18': '春节',
  '02-19': '春节',
  '04-04': '清明',
  '05-01': '劳动节',
  '05-02': '劳动节',
  '06-19': '端午',
  '09-25': '中秋',
  '10-01': '国庆',
  '10-02': '国庆',
  '10-03': '国庆',
};

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

export function festivalName(iso: string): string {
  return FESTIVAL_BY_MD[iso.slice(5)] ?? '';
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
      return '官方尚未发布';
    case 'REJECTED':
      return '已驳回';
    default:
      return status || '未知';
  }
}

export function buildDayRows(
  version: BpmOAOvertimeCalendarApi.Version,
): CalendarDayRow[] {
  const legal = new Set(parseJsonList(version.legalHolidaysJson));
  const work = new Set(parseJsonList(version.makeupWorkdaysJson));
  const rest = new Set(parseJsonList(version.makeupRestDaysJson));
  const weekends = new Set(parseJsonList(version.weekendsJson));
  const dates = [...new Set([...legal, ...work, ...rest, ...weekends])].sort();
  return dates.map((date) => {
    let kind: CalendarDayKind = 'WEEKDAY';
    if (legal.has(date)) {
      kind = 'LEGAL_HOLIDAY';
    } else if (work.has(date)) {
      kind = 'MAKEUP_WORKDAY';
    } else if (rest.has(date)) {
      kind = 'MAKEUP_REST';
    } else if (weekends.has(date)) {
      kind = 'WEEKEND';
    }
    return {
      date,
      weekday: weekdayLabel(date),
      festival: festivalName(date),
      kind,
      kindLabel: kindLabel(kind),
      allowed: kind === 'LEGAL_HOLIDAY' || kind === 'WEEKEND',
    };
  });
}
