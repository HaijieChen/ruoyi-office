import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  confirmClaim,
  createClaim,
  getMyClaim,
  getMyClaimPage,
  getReviewClaim,
  getReviewPage,
  getRevokeAuditList,
  rejectClaim,
  resubmitClaim,
  revokeClaim,
  updateClaim,
} from '#/api/finance/receipt-claim';
import { requestClient } from '#/api/request';

vi.mock('#/api/request', () => ({
  requestClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
  },
}));

const mock = vi.mocked(requestClient);

beforeEach(() => {
  vi.clearAllMocks();
});

describe('FinanceReceiptClaimApi — endpoint contracts', () => {
  it('POST /create with save body', () => {
    const data = {
      remark: '测试',
      items: [{ receiptId: 1, businessOrderId: 2, claimAmount: 100 }],
    };
    createClaim(data);
    expect(mock.post).toHaveBeenCalledWith('/finance/receipt-claim/create', data);
  });

  it('PUT /update with save body including id', () => {
    const data = {
      id: 5,
      items: [{ receiptId: 3, businessOrderId: 4, claimAmount: 200 }],
    };
    updateClaim(data);
    expect(mock.put).toHaveBeenCalledWith('/finance/receipt-claim/update', data);
  });

  it('GET /my-page with query params', () => {
    const params = { pageNo: 1, pageSize: 10, status: 0 as const };
    getMyClaimPage(params);
    expect(mock.get).toHaveBeenCalledWith(
      '/finance/receipt-claim/my-page',
      { params },
    );
  });

  it('GET /review-page with query params', () => {
    const params = { pageNo: 1, pageSize: 20, claimantId: 42 };
    getReviewPage(params);
    expect(mock.get).toHaveBeenCalledWith(
      '/finance/receipt-claim/review-page',
      { params },
    );
  });

  it('GET /get with id param', () => {
    getMyClaim(7);
    expect(mock.get).toHaveBeenCalledWith(
      '/finance/receipt-claim/get',
      { params: { id: 7 } },
    );
  });

  it('GET /review-get with id param', () => {
    getReviewClaim(9);
    expect(mock.get).toHaveBeenCalledWith(
      '/finance/receipt-claim/review-get',
      { params: { id: 9 } },
    );
  });

  it('PUT /confirm?id= as query param', () => {
    confirmClaim(11);
    expect(mock.put).toHaveBeenCalledWith(
      '/finance/receipt-claim/confirm',
      undefined,
      { params: { id: 11 } },
    );
  });

  it('PUT /reject with body {id, reason}', () => {
    const data = { id: 13, reason: '金额不符' };
    rejectClaim(data);
    expect(mock.put).toHaveBeenCalledWith(
      '/finance/receipt-claim/reject',
      data,
    );
  });

  it('PUT /resubmit?id= as query param', () => {
    resubmitClaim(15);
    expect(mock.put).toHaveBeenCalledWith(
      '/finance/receipt-claim/resubmit',
      undefined,
      { params: { id: 15 } },
    );
  });

  it('PUT /revoke with body {id, reason}', () => {
    const data = { id: 17, reason: '认领金额有误' };
    revokeClaim(data);
    expect(mock.put).toHaveBeenCalledWith(
      '/finance/receipt-claim/revoke',
      data,
    );
  });

  it('GET /revoke-audit-list with claimId param', () => {
    getRevokeAuditList(19);
    expect(mock.get).toHaveBeenCalledWith(
      '/finance/receipt-claim/revoke-audit-list',
      { params: { claimId: 19 } },
    );
  });
});
