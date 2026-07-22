import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  createBusinessOrder,
  deleteBusinessOrder,
  getBusinessOrder,
  getBusinessOrderPage,
  updateBusinessOrder,
} from '#/api/finance/business-order';
import { requestClient } from '#/api/request';

vi.mock('#/api/request', () => ({
  requestClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

const mock = vi.mocked(requestClient);

beforeEach(() => {
  vi.clearAllMocks();
});

describe('FinanceBusinessOrderApi — endpoint contracts', () => {
  it('getBusinessOrderPage calls /finance/business-order/page with params', () => {
    const params = { pageNo: 1, pageSize: 20, orderNo: 'BO-001' };
    getBusinessOrderPage(params);
    expect(mock.get).toHaveBeenCalledWith('/finance/business-order/page', {
      params,
    });
  });

  it('getBusinessOrder calls /finance/business-order/get with id param', () => {
    getBusinessOrder(42);
    expect(mock.get).toHaveBeenCalledWith('/finance/business-order/get', {
      params: { id: 42 },
    });
  });

  it('createBusinessOrder POSTs to /finance/business-order/create', () => {
    const form = {
      orderNo: 'BO-001',
      businessSubject: '测试主体',
      businessType: '采购',
      receivableAmount: 1000,
      payableAmount: 0,
      currency: 'CNY',
      ownerId: 1,
      status: 0 as const,
    };
    createBusinessOrder(form);
    expect(mock.post).toHaveBeenCalledWith('/finance/business-order/create', form);
  });

  it('updateBusinessOrder PUTs to /finance/business-order/update', () => {
    const form = {
      id: 7,
      orderNo: 'BO-007',
      businessSubject: '测试主体',
      businessType: '销售',
      receivableAmount: 500,
      payableAmount: 200,
      currency: 'USD',
      ownerId: 2,
      status: 1 as const,
    };
    updateBusinessOrder(form);
    expect(mock.put).toHaveBeenCalledWith('/finance/business-order/update', form);
  });

  it('deleteBusinessOrder DELETEs with comma-joined ids param', () => {
    deleteBusinessOrder([1, 2, 3]);
    expect(mock.delete).toHaveBeenCalledWith('/finance/business-order/delete', {
      params: { ids: '1,2,3' },
    });
  });

  it('deleteBusinessOrder DELETEs a single id without trailing comma', () => {
    deleteBusinessOrder([5]);
    expect(mock.delete).toHaveBeenCalledWith('/finance/business-order/delete', {
      params: { ids: '5' },
    });
  });
});
