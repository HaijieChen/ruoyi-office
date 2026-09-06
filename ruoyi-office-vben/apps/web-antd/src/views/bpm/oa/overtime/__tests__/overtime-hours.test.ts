import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import { CALENDAR_MISSING_ERROR } from '../overtime-calendar';
import {
  calcOvertimeHours,
  combineDateAndTime,
  getOvertimeRangeError,
  overtimeHoursField,
  previewOvertimeHours,
  splitDateAndTime,
  splitOvertimeSlices,
} from '../overtime-hours';

const TOO_SHORT_ERROR = '加班时长不能少于 2 小时';
const END_BEFORE_START_ERROR = '结束时间必须晚于开始时间';
const TYPE_MISMATCH_ERROR =
  '加班类型与日期不一致：法定节假日请选「是」，普通周末请选「否」';

function at(hour: number, minute = 0, day = 6, monthIndex = 8, year = 2026) {
  return new Date(year, monthIndex, day, hour, minute, 0, 0).getTime();
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
    expect(getOvertimeRangeError(at(10), at(11))).toBe(TOO_SHORT_ERROR);
    expect(overtimeHoursField(at(10), at(11)).error).toBe(TOO_SHORT_ERROR);
  });

  it('rejects 119 minutes even when rounded hours look like 2.0', () => {
    const start = at(10, 0);
    const end = at(11, 59);
    expect(previewOvertimeHours(start, end)).toBe(2);
    expect(calcOvertimeHours(start, end)).toBeUndefined();
    expect(getOvertimeRangeError(start, end)).toBe(TOO_SHORT_ERROR);
  });

  it('counts 10:00-12:00 as 2.0', () => {
    expect(calcOvertimeHours(at(10), at(12))).toBe(2);
  });

  it('caps 10:00-23:00 at 8.0', () => {
    expect(previewOvertimeHours(at(10), at(23))).toBe(8);
    expect(calcOvertimeHours(at(10), at(23))).toBe(8);
  });

  it('allows Saturday 23:00 to Sunday 01:00 as 2.0', () => {
    const start = at(23, 0, 5);
    const end = at(1, 0, 6);
    expect(splitOvertimeSlices(start, end)).toHaveLength(2);
    expect(calcOvertimeHours(start, end)).toBe(2);
    expect(getOvertimeRangeError(start, end, 'false')).toBeUndefined();
  });

  it('rejects screenshot span for workdays inside', () => {
    const start = combineDateAndTime('2026-09-06', '09:00');
    const end = combineDateAndTime('2026-09-08', '19:00');
    const field = overtimeHoursField(start, end, 'false');
    expect(field.hours).toBe(24);
    expect(field.error).toContain('2026-09-07');
    expect(field.error).toContain('2026-09-08');
    expect(field.error).not.toBe(TOO_SHORT_ERROR);
  });

  it('does not add a next-day zero slice at exact midnight', () => {
    const start = at(22, 0, 6);
    const end = at(0, 0, 7);
    const slices = splitOvertimeSlices(start, end);
    expect(slices).toHaveLength(1);
    expect(slices[0]?.day).toBe('2026-09-06');
    expect(calcOvertimeHours(start, end)).toBe(2);
  });

  it('rejects weekday and makeup rest', () => {
    expect(getOvertimeRangeError(at(10, 0, 7), at(12, 0, 7))).toContain(
      '2026-09-07',
    );
    const makeupRest = combineDateAndTime('2026-02-20', '10:00');
    const makeupRestEnd = combineDateAndTime('2026-02-20', '12:00');
    expect(getOvertimeRangeError(makeupRest, makeupRestEnd)).toContain(
      '2026-02-20',
    );
  });

  it('rejects weekend marked as legal holiday', () => {
    expect(getOvertimeRangeError(at(10), at(12), 'true')).toBe(
      TYPE_MISMATCH_ERROR,
    );
  });

  it('rejects missing calendar year', () => {
    const start = combineDateAndTime('2027-01-01', '10:00');
    const end = combineDateAndTime('2027-01-01', '12:00');
    expect(getOvertimeRangeError(start, end, 'true')).toBe(
      CALENDAR_MISSING_ERROR,
    );
  });

  it('same-day 09:00-19:00 caps at 8.0 and has no range error on Sunday', () => {
    const start = combineDateAndTime('2026-09-06', '09:00');
    const end = combineDateAndTime('2026-09-06', '19:00');
    const field = overtimeHoursField(start, end, 'false');
    expect(field.hours).toBe(8);
    expect(field.error).toBeUndefined();
    expect(calcOvertimeHours(start, end)).toBe(8);
  });

  it('has no error while date or clock is missing, then computes after fill', () => {
    expect(overtimeHoursField(undefined, at(12)).error).toBeUndefined();
    expect(overtimeHoursField(undefined, at(12)).hours).toBeUndefined();
    const start = combineDateAndTime('2026-09-06', '10:00');
    expect(overtimeHoursField(start, undefined).error).toBeUndefined();
    const filled = overtimeHoursField(
      start,
      combineDateAndTime('2026-09-06', '12:00'),
      'false',
    );
    expect(filled.hours).toBe(2);
    expect(filled.error).toBeUndefined();
  });

  it('recomputes when either start or end changes', () => {
    let start = combineDateAndTime('2026-09-06', '10:00');
    let end = combineDateAndTime('2026-09-06', '13:00');
    expect(overtimeHoursField(start, end, 'false').hours).toBe(3);
    end = combineDateAndTime('2026-09-06', '12:00');
    expect(overtimeHoursField(start, end, 'false').hours).toBe(2);
    end = combineDateAndTime('2026-09-07', '12:00');
    const crossed = overtimeHoursField(start, end, 'false');
    expect(crossed.hours).toBe(16);
    expect(crossed.error).toContain('2026-09-07');
  });

  it('clears hours and error after range is emptied', () => {
    const filled = overtimeHoursField(at(10), at(12));
    expect(filled.hours).toBe(2);
    const cleared = overtimeHoursField(undefined, undefined);
    expect(cleared.hours).toBeUndefined();
    expect(cleared.error).toBeUndefined();
  });

  it('round-trips existing application start/end for echo', () => {
    const start = combineDateAndTime('2026-09-06', '09:00');
    const parts = splitDateAndTime(start);
    expect(parts).toEqual({ date: '2026-09-06', clock: '09:00' });
    expect(combineDateAndTime(parts?.date, parts?.clock)).toBe(start);
    expect(splitDateAndTime('2026-09-06T09:00:00')).toEqual({
      date: '2026-09-06',
      clock: '09:00',
    });
  });

  it('rejects equal start and end', () => {
    expect(calcOvertimeHours(at(10), at(10))).toBeUndefined();
    expect(getOvertimeRangeError(at(10), at(10))).toBe(
      END_BEFORE_START_ERROR,
    );
  });

  it('rounds minutes/60 HALF_UP to 1 decimal', () => {
    expect(calcOvertimeHours(at(10, 0), at(12, 3))).toBe(2.1);
  });

  it('shows end-before-start instead of a blank hours field', () => {
    const field = overtimeHoursField(at(19), at(9));
    expect(field.hours).toBeUndefined();
    expect(field.error).toBe(END_BEFORE_START_ERROR);
  });

  it('binds hours-field error on the overtime form body', () => {
    const vuePath = join(
      dirname(fileURLToPath(import.meta.url)),
      '../modules/form-body.vue',
    );
    const src = readFileSync(vuePath, 'utf8');
    expect(src).toContain('overtimeHoursField');
    expect(src).toMatch(/label="加班时长"[\s\S]*hoursFieldError/);
    expect(src).toContain(':validate-status');
    expect(src).toContain(':help="hoursFieldError');
    expect(src).toContain('formData.value.holiday');
  });
});
