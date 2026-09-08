import assert from 'node:assert/strict';
import { test } from 'node:test';
import { formatLeaveDuration } from '../../ruoyi-office-vben/apps/web-antd/src/views/bpm/oa/leave/leave-duration.ts';

test('same-day interval retains hours, minutes and seconds', () => {
  assert.equal(formatLeaveDuration('2026-09-08 09:15:00', '2026-09-08 11:45:30'), '2 小时 30 分钟 30 秒');
});
test('cross-day interval is not truncated to calendar dates', () => {
  assert.equal(formatLeaveDuration([2026, 9, 8, 23, 30], [2026, 9, 9, 1, 0]), '1 小时 30 分钟');
  assert.equal(formatLeaveDuration('2026-09-08T09:00:00', '2026-09-10T10:00:00'), '49 小时');
});
test('supports epoch seconds, milliseconds and timestamp strings', () => {
  for (const [start, end] of [[1788829200, 1788834600], [1788829200000, 1788834600000], ['1788829200000', '1788834600000']]) {
    assert.equal(formatLeaveDuration(start, end), '1 小时 30 分钟');
  }
});
test('missing, invalid and reversed intervals do not invent duration', () => {
  for (const [start, end] of [[null, null], ['', ''], ['invalid', '2026-09-08'], [2e12, 1e12]]) {
    assert.equal(formatLeaveDuration(start, end), '—');
  }
  assert.equal(formatLeaveDuration(1788829200000, 1788829200000), '0 小时');
});
