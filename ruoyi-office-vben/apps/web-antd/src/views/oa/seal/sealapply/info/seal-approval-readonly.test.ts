import { describe, expect, it } from 'vitest';

import {
  actualReturnTimeError,
  buildSealApprovalFields,
  mergeSealSavePayload,
  shouldRequireActualReturnTime,
} from './seal-approval-readonly';

describe('buildSealApprovalFields', () => {
  it('shows full title, dict labels and formatted use time', () => {
    const rows = buildSealApprovalFields({
      sealName: '公章',
      useType: 2,
      useMode: 1,
      documentTitle: 'TEST-谢君剑测试用印流程',
      documentCount: 1,
      expectedUseTime: Date.parse('2026-09-12T16:23:47'),
      isUrgent: 0,
      cause: '测试一下 用印流程是否可以正常流转',
      useTypeOptions: [
        { label: '合同用章', value: 1 },
        { label: '其他用章', value: 2 },
      ],
      useModeOptions: [{ label: '现场用印', value: 1 }],
      urgentOptions: [
        { label: '开启', value: 1 },
        { label: '关闭', value: 0 },
      ],
    });
    const byKey = Object.fromEntries(rows.map((row) => [row.key, row]));
    expect(byKey.documentTitle.value).toBe('TEST-谢君剑测试用印流程');
    expect(byKey.useType.value).toBe('其他用章');
    expect(byKey.useMode.value).toBe('现场用印');
    expect(byKey.expectedUseTime.value).toContain('2026-09-12');
    expect(byKey.expectedUseTime.value).toContain('16:23:47');
    expect(byKey.isUrgent.value).toBe('关闭');
    expect(byKey.cause.value).toContain('测试一下');
    expect(byKey.cause.fullWidth).toBe(true);
    expect(rows.some((row) => row.key === 'contractAmount')).toBe(false);
  });

  it('keeps empty values visible as empty not truncated controls', () => {
    const rows = buildSealApprovalFields({
      sealName: '公章',
      useType: 2,
      documentType: '',
      expectedReturnTime: null,
    });
    const byKey = Object.fromEntries(rows.map((row) => [row.key, row]));
    expect(byKey.documentType.empty).toBe(true);
    expect(byKey.expectedReturnTime.empty).toBe(true);
    expect(byKey.documentType.value).toBe('');
  });

  it('still requires return time on keeper node when isTodo is missing', () => {
    expect(
      shouldRequireActualReturnTime({
        isApproval: true,
        nodeKeyName: '申请人归还印章',
      }),
    ).toBe(true);
    expect(
      shouldRequireActualReturnTime({
        isApproval: true,
        nodeKeyName: '部门经理',
      }),
    ).toBe(false);
  });

  it('blocks empty or invalid return time even when canReturnEdit is false', () => {
    expect(
      shouldRequireActualReturnTime({
        isApproval: true,
        nodeKeyName: '申请人归还印章',
      }),
    ).toBe(true);
    expect(actualReturnTimeError(undefined)).toBe('请选择实际归还时间');
    expect(actualReturnTimeError('')).toBe('请选择实际归还时间');
    expect(actualReturnTimeError('Invalid Date')).toBe('请选择实际归还时间');
    expect(actualReturnTimeError(Number.NaN)).toBe('请选择实际归还时间');
    expect(actualReturnTimeError(1_778_200_000_000)).toBeNull();
  });

  it('keeps DatePicker actualReturnTime over stale hidden form values', () => {
    const payload = mergeSealSavePayload(
      { id: 19, actualReturnTime: 1_778_200_000_000, cause: 'new' },
      { id: 19, actualReturnTime: 1_700_000_000_000, cause: 'old-form' },
      true,
    );
    expect(payload.actualReturnTime).toBe(1_778_200_000_000);
    expect(payload.cause).toBe('old-form');
  });

  it('formats ISO timestamp without leftover T', () => {
    const rows = buildSealApprovalFields({
      expectedUseTime: '2026-09-12T16:23:47',
    });
    const useTime = rows.find((row) => row.key === 'expectedUseTime')?.value || '';
    expect(useTime).toContain('2026-09-12');
    expect(useTime).toContain('16:23:47');
    expect(useTime).not.toContain('T');
  });
});
