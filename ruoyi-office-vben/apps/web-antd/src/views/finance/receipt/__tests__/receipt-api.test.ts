import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  getUnclaimedReceiptPage,
  importBankReceipt,
  mapFailureRows,
} from '#/api/finance/receipt';
import { requestClient } from '#/api/request';

vi.mock('#/api/request', () => ({
  requestClient: {
    get: vi.fn(),
    upload: vi.fn(),
  },
}));

const mockedRequestClient = vi.mocked(requestClient);

beforeEach(() => {
  vi.clearAllMocks();
});

describe('mapFailureRows — production implementation', () => {
  it('returns an empty array for an empty record', () => {
    expect(mapFailureRows({})).toEqual([]);
  });

  it('converts a single-entry record to a display row with a label', () => {
    const result = mapFailureRows({ 3: '金额格式错误' });
    expect(result).toHaveLength(1);
    expect(result[0]).toMatchObject({
      rowNum: 3,
      reason: '金额格式错误',
      label: '第 3 行：金额格式错误',
    });
  });

  it('sorts rows by rowNum in ascending order', () => {
    const result = mapFailureRows({ 10: '缺少必填列', 2: '日期格式错误', 5: '重复流水号' });
    expect(result.map((r) => r.rowNum)).toEqual([2, 5, 10]);
  });

  it('preserves rowNum (as number) and reason fields unchanged', () => {
    const result = mapFailureRows({ 1: '缺少必填列', 5: '日期格式错误' });
    expect(result[0]).toMatchObject({ rowNum: 1, reason: '缺少必填列' });
    expect(result[1]).toMatchObject({ rowNum: 5, reason: '日期格式错误' });
  });

  it('formats label string correctly for large row numbers', () => {
    const result = mapFailureRows({ 100: '重复的银行流水号' });
    expect(result[0]!.label).toBe('第 100 行：重复的银行流水号');
  });
});

describe('FinanceBankReceiptApi — endpoint contracts', () => {
  it('calls the unclaimed page backend endpoint with query params', () => {
    const params = { pageNo: 1, pageSize: 10, receiptNo: 'RC-20260722-1' };

    getUnclaimedReceiptPage(params);

    expect(mockedRequestClient.get).toHaveBeenCalledWith(
      '/finance/receipt/unclaimed-page',
      { params },
    );
  });

  it('uploads the selected xlsx file to the import backend endpoint', () => {
    const file = new File(['receipt'], 'receipt.xlsx');

    importBankReceipt(file);

    expect(mockedRequestClient.upload).toHaveBeenCalledWith(
      '/finance/receipt/import',
      { file },
    );
  });
});
