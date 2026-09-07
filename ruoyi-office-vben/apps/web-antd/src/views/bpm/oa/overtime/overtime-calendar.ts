export type OvertimeDayKind =
  | 'LEGAL_HOLIDAY'
  | 'WEEKEND'
  | 'MAKEUP_WORKDAY'
  | 'MAKEUP_REST'
  | 'WEEKDAY';

const LEGAL_2026 = new Set([
  '2026-01-01',
  '2026-02-16',
  '2026-02-17',
  '2026-02-18',
  '2026-02-19',
  '2026-04-05',
  '2026-05-01',
  '2026-05-02',
  '2026-06-19',
  '2026-09-25',
  '2026-10-01',
  '2026-10-02',
  '2026-10-03',
]);

const MAKEUP_WORK_2026 = new Set([
  '2026-01-04',
  '2026-02-14',
  '2026-02-28',
  '2026-05-09',
  '2026-09-20',
  '2026-10-10',
]);

const MAKEUP_REST_2026 = new Set([
  '2026-01-02',
  '2026-02-20',
  '2026-02-23',
  '2026-04-06',
  '2026-05-04',
  '2026-05-05',
  '2026-10-05',
  '2026-10-06',
  '2026-10-07',
]);

const YEARS = new Set([2026]);

export const CALENDAR_MISSING_ERROR =
  '该年度节假日日历尚未发布或配置，请联系人事';

export function hasOvertimeCalendarYear(year: number): boolean {
  return YEARS.has(year);
}

export function classifyOvertimeDay(day: string): OvertimeDayKind {
  const year = Number(day.slice(0, 4));
  if (!YEARS.has(year)) {
    throw new Error('NO_CALENDAR');
  }
  if (LEGAL_2026.has(day)) {
    return 'LEGAL_HOLIDAY';
  }
  if (MAKEUP_WORK_2026.has(day)) {
    return 'MAKEUP_WORKDAY';
  }
  if (MAKEUP_REST_2026.has(day)) {
    return 'MAKEUP_REST';
  }
  const [y, m, d] = day.split('-').map(Number);
  const dow = new Date(y, m - 1, d).getDay();
  if (dow === 0 || dow === 6) {
    return 'WEEKEND';
  }
  return 'WEEKDAY';
}

export function overtimeDayAllowed(kind: OvertimeDayKind): boolean {
  return kind === 'LEGAL_HOLIDAY' || kind === 'WEEKEND';
}
