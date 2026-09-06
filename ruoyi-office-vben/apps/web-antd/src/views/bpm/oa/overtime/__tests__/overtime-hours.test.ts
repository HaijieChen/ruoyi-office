import { describe, expect, it } from 'vitest';

import {
  calcOvertimeHours,
  combineDateAndTime,
  getOvertimeRangeError,
  previewOvertimeHours,
} from '../overtime-hours';

function at(hour: number, minute = 0, day = 6) {
  return new Date(2026, 8, day, hour, minute, 0, 0).getTime();
}

describe('overtime hours helper', () => {
  it('combines date and clock into local timestamp', () => {
    expect(combineDateAndTime('2026-09-06', '10:00')).toBe(at(10, 0));
    expect(combineDateAndTime('2026-09-06', '10:00:00')).toBe(at(10, 0));
    expect(combineDateAndTime('', '10:00')).toBeUndefined();
  });

  it('rejects under 2 hours including 10:00-11:00', () => {
    expect(previewOvertimeHours(at(10), at(11))).toBe(1);
    expect(calcOvertimeHours(at(10), at(11))).toBeUndefined();
    expect(getOvertimeRangeError(at(10), at(11))).toBe('加班时长至少 2 小时');
  });

  it('counts 10:00-12:00 as 2.0', () => {
    expect(calcOvertimeHours(at(10), at(12))).toBe(2);
  });

  it('caps 10:00-23:00 at 8.0', () => {
    expect(previewOvertimeHours(at(10), at(23))).toBe(8);
    expect(calcOvertimeHours(at(10), at(23))).toBe(8);
  });

  it('rejects cross-day ranges', () => {
    expect(calcOvertimeHours(at(10, 0, 6), at(10, 0, 7))).toBeUndefined();
    expect(getOvertimeRangeError(at(10, 0, 6), at(10, 0, 7))).toBe(
      '开始与结束必须为同一天',
    );
  });

  it('rejects equal start and end', () => {
    expect(calcOvertimeHours(at(10), at(10))).toBeUndefined();
    expect(getOvertimeRangeError(at(10), at(10))).toBe(
      '结束时间必须晚于开始时间',
    );
  });

  it('rounds minutes/60 HALF_UP to 1 decimal', () => {
    expect(calcOvertimeHours(at(10, 0), at(12, 3))).toBe(2.1);
  });
});
