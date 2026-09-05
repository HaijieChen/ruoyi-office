import { beforeEach, describe, expect, it, vi } from 'vitest';

import { requestClient } from '#/api/request';

import {
  createAndStartSealApplyBill,
  submitSealApplyBill,
  type SealApplyBillApi,
} from './index';

vi.mock('#/api/request', () => ({
  requestClient: { post: vi.fn() },
}));

beforeEach(() => vi.clearAllMocks());

function request(): SealApplyBillApi.SealApplyBill {
  return {
    billCode: '',
    sealId: 95001,
    sealNo: 'S7-ONLY',
    cause: 'synthetic',
    useType: 1,
    useMode: 1,
    companyId: 92000,
    companyName: 'Synthetic',
    deptId: 92001,
    deptName: 'Synthetic',
    expectedUseTime: new Date(2026, 8, 7, 19, 44, 5, 283),
  };
}

describe('unified seal start date wire contract', () => {
  it('posts both Date values as local wall-clock strings, never ISO Z', () => {
    const data = {
      ...request(),
      expectedReturnTime: new Date(2026, 8, 8, 20, 15, 6),
    };
    createAndStartSealApplyBill(data);
    expect(requestClient.post).toHaveBeenCalledWith(
      '/oa/seal-apply-bill/create-and-start',
      {
        ...data,
        expectedUseTime: '2026-09-07 19:44:05',
        expectedReturnTime: '2026-09-08 20:15:06',
      },
    );
    expect(data.expectedUseTime).toBeInstanceOf(Date);
    expect(data.expectedReturnTime).toBeInstanceOf(Date);
  });

  it('keeps absent optional dates undefined instead of inventing the current time', () => {
    const data = { ...request(), expectedUseTime: undefined };
    createAndStartSealApplyBill(data);
    expect(requestClient.post).toHaveBeenCalledWith(
      '/oa/seal-apply-bill/create-and-start',
      {
        ...data,
        expectedUseTime: undefined,
        expectedReturnTime: undefined,
      },
    );
  });

  it('preserves an absent return date when the required use date is present', () => {
    const data = request();
    createAndStartSealApplyBill(data);
    expect(requestClient.post).toHaveBeenCalledWith(
      '/oa/seal-apply-bill/create-and-start',
      {
        ...data,
        expectedUseTime: '2026-09-07 19:44:05',
        expectedReturnTime: undefined,
      },
    );
  });

  it('does not change legacy submission payload semantics', () => {
    const data = request();
    submitSealApplyBill(data);
    expect(requestClient.post).toHaveBeenCalledWith(
      '/oa/seal-apply-bill/submit',
      data,
    );
    expect(vi.mocked(requestClient.post).mock.calls[0]?.[1]).toBe(data);
  });
});
