import { describe, expect, it } from 'vitest';

import {
  buildDayRows,
  festivalName,
  isSeedVersion,
  statusLabel,
  weekdayLabel,
} from './calendar-view';

describe('overtime calendar view', () => {
  it('builds 2026 legal thirteen and classifies May 3 as weekend', () => {
    const rows = buildDayRows({
      legalHolidaysJson:
        '["2026-01-01","2026-02-16","2026-02-17","2026-02-18","2026-02-19","2026-04-04","2026-05-01","2026-05-02","2026-06-19","2026-09-25","2026-10-01","2026-10-02","2026-10-03"]',
      makeupWorkdaysJson:
        '["2026-01-04","2026-02-14","2026-02-28","2026-05-09","2026-09-20","2026-10-10"]',
      makeupRestDaysJson:
        '["2026-01-02","2026-02-20","2026-02-23","2026-04-06","2026-05-04","2026-05-05","2026-10-05","2026-10-06","2026-10-07"]',
      weekendsJson: '["2026-05-03"]',
    });
    const legal = rows.filter((row) => row.kind === 'LEGAL_HOLIDAY');
    expect(legal).toHaveLength(13);
    expect(legal.map((row) => row.date)).toContain('2026-05-01');
    expect(legal.map((row) => row.date)).not.toContain('2026-05-03');
    const may3 = rows.find((row) => row.date === '2026-05-03');
    expect(may3?.kind).toBe('WEEKEND');
    expect(may3?.allowed).toBe(true);
    expect(festivalName('2026-05-01')).toBe('劳动节');
    expect(weekdayLabel('2026-05-01')).toBe('五');
  });

  it('marks seed versions and failed status without calling them success', () => {
    expect(
      isSeedVersion({
        contentHash: 'seed-2026-legal13-labor2',
        parseNote: '国令第795号',
      }),
    ).toBe(true);
    expect(statusLabel('FAILED')).toBe('采集失败');
    expect(statusLabel('ACTIVE')).toBe('已生效');
  });
});
