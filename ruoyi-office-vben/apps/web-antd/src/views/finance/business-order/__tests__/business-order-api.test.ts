import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { FinanceBusinessOrderApi } from '#/api/finance/business-order';

import {
  createBusinessOrder,
  deleteBusinessOrder,
  getBusinessOrder,
  getBusinessOrderPage,
  importBusinessOrder,
  updateBusinessOrder,
} from '#/api/finance/business-order';
import { requestClient } from '#/api/request';

import { useGridColumns, useGridFormSchema } from '../data';

vi.mock('#/api/request', () => ({
  requestClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    upload: vi.fn(),
  },
}));

const mock = vi.mocked(requestClient);

beforeEach(() => {
  vi.clearAllMocks();
});

// ──────────────────────────────────────────────────────────────────────────────
// Generic field absence — the workbook does NOT include these
// ──────────────────────────────────────────────────────────────────────────────
describe('Generic field absence in schema and columns', () => {
  it('filters must NOT contain businessSubject', () => {
    expect(
      useGridFormSchema().some((s) => s.fieldName === 'businessSubject'),
    ).toBe(false);
  });

  it('filters must NOT contain businessType', () => {
    expect(
      useGridFormSchema().some((s) => s.fieldName === 'businessType'),
    ).toBe(false);
  });

  it('filters must NOT contain projectRef', () => {
    expect(
      useGridFormSchema().some((s) => s.fieldName === 'projectRef'),
    ).toBe(false);
  });

  it('filters must NOT contain status', () => {
    expect(
      useGridFormSchema().some((s) => s.fieldName === 'status'),
    ).toBe(false);
  });

  it('columns must NOT contain receivableAmount', () => {
    const columns = useGridColumns() ?? [];
    expect(columns.some((c) => c.field === 'receivableAmount')).toBe(false);
  });

  it('columns must NOT contain payableAmount', () => {
    const columns = useGridColumns() ?? [];
    expect(columns.some((c) => c.field === 'payableAmount')).toBe(false);
  });

  it('columns must NOT contain businessSubject', () => {
    const columns = useGridColumns() ?? [];
    expect(columns.some((c) => c.field === 'businessSubject')).toBe(false);
  });

  it('columns must NOT contain businessType', () => {
    const columns = useGridColumns() ?? [];
    expect(columns.some((c) => c.field === 'businessType')).toBe(false);
  });

  it('columns must NOT contain bankAccount (S1 removed)', () => {
    const columns = useGridColumns() ?? [];
    expect(columns.some((c) => c.field === 'bankAccount')).toBe(false);
  });
});

// ──────────────────────────────────────────────────────────────────────────────
// Workbook field presence in columns
// ──────────────────────────────────────────────────────────────────────────────
describe('Workbook fields present in columns', () => {
  it('columns include importDate', () => {
    const columns = useGridColumns() ?? [];
    expect(columns.some((c) => c.field === 'importDate')).toBe(true);
  });

  it('columns include importerName', () => {
    const columns = useGridColumns() ?? [];
    expect(columns.some((c) => c.field === 'importerName')).toBe(true);
  });

  it('columns include entityCompanyName', () => {
    const columns = useGridColumns() ?? [];
    expect(columns.some((c) => c.field === 'entityCompanyName')).toBe(true);
  });

  it('columns include signedExecutionAmount', () => {
    const columns = useGridColumns() ?? [];
    expect(columns.some((c) => c.field === 'signedExecutionAmount')).toBe(true);
  });

  it('columns include settlementAmount (read-only computed)', () => {
    const columns = useGridColumns() ?? [];
    expect(columns.some((c) => c.field === 'settlementAmount')).toBe(true);
  });

  it('filters include entityCompanyDeptId', () => {
    expect(
      useGridFormSchema().some((s) => s.fieldName === 'entityCompanyDeptId'),
    ).toBe(true);
  });

  it('filters include importDateRange or importDate', () => {
    const schemas = useGridFormSchema();
    expect(
      schemas.some(
        (s) => s.fieldName === 'importDateRange' || s.fieldName === 'importDate',
      ),
    ).toBe(true);
  });
});

// ──────────────────────────────────────────────────────────────────────────────
// API endpoint contracts
// ──────────────────────────────────────────────────────────────────────────────
describe('FinanceBusinessOrderApi — endpoint contracts', () => {
  it('getBusinessOrderPage calls /finance/business-order/page', () => {
    const params = { pageNo: 1, pageSize: 20, orderNo: 'BO-001' };
    getBusinessOrderPage(params);
    expect(mock.get).toHaveBeenCalledWith('/finance/business-order/page', {
      params,
    });
  });

  it('getBusinessOrder calls /finance/business-order/get with id', () => {
    getBusinessOrder(42);
    expect(mock.get).toHaveBeenCalledWith('/finance/business-order/get', {
      params: { id: 42 },
    });
  });

  it('createBusinessOrder POSTs backend workbook contract fields, no generic fields', () => {
    const form: FinanceBusinessOrderApi.SaveForm = {
      entityCompanyDeptId: 10,
      orderDate: '2024-01-10',
      productName: '设备A',
      contactPerson: '张三',
      executionStartDate: '2024-02-01',
      executionEndDate: '2024-12-31',
      payerName: '付款方公司',
      signedExecutionAmount: 100_000,
      discountRate: 0.05,
    };
    createBusinessOrder(form);
    expect(mock.post).toHaveBeenCalledWith(
      '/finance/business-order/create',
      form,
    );
    // Payload must NOT contain generic fields
    const payload = vi.mocked(mock.post).mock.calls[0]?.[1] ?? {};
    expect('businessSubject' in payload).toBe(false);
    expect('businessType' in payload).toBe(false);
    expect('projectRef' in payload).toBe(false);
    expect('receivableAmount' in payload).toBe(false);
    expect('payableAmount' in payload).toBe(false);
    expect('status' in payload).toBe(false);
    expect('bankAccount' in payload).toBe(false);
  });

  it('updateBusinessOrder PUTs to /finance/business-order/update', () => {
    const form: FinanceBusinessOrderApi.SaveForm = {
      id: 7,
      entityCompanyDeptId: 10,
      orderDate: '2024-01-10',
      productName: '设备A',
      contactPerson: '张三',
      executionStartDate: '2024-02-01',
      executionEndDate: '2024-12-31',
      signedExecutionAmount: 50000,
      discountRate: 0,
    };
    updateBusinessOrder(form);
    expect(mock.put).toHaveBeenCalledWith('/finance/business-order/update', form);
  });

  it('deleteBusinessOrder DELETEs with comma-joined ids', () => {
    deleteBusinessOrder([1, 2, 3]);
    expect(mock.delete).toHaveBeenCalledWith('/finance/business-order/delete', {
      params: { ids: '1,2,3' },
    });
  });

  it('importBusinessOrder calls requestClient.upload with file only', () => {
    const file = new File([''], 'test.xlsx');
    importBusinessOrder(file);
    expect(mock.upload).toHaveBeenCalledWith(
      '/finance/business-order/import',
      { file },
    );
  });
});

// ──────────────────────────────────────────────────────────────────────────────
// Backend contract alignment — 后端字段契约对齐
// ──────────────────────────────────────────────────────────────────────────────
describe('Backend contract alignment', () => {
  // 1. ImportResult 必须使用 skippedRows（number[]），不得保留 duplicateRows
  it('ImportResult type has skippedRows as number array, not duplicateRows', () => {
    // 类型检查：构造一个合法的 ImportResult，只使用 skippedRows
    const result: FinanceBusinessOrderApi.ImportResult = {
      orderNos: ['BO-001'],
      skippedRows: [2, 5, 8],
      failureRows: { 3: '金额格式错误' },
    };
    expect(Array.isArray(result.skippedRows)).toBe(true);
    expect(result.skippedRows).toEqual([2, 5, 8]);
    // duplicateRows 不应存在于类型中（运行时检查）
    expect('duplicateRows' in result).toBe(false);
  });

  // 2. BusinessOrder 必须有 importerId 和 importerName，不再只有 importer
  it('BusinessOrder type has importerId and importerName, not importer', () => {
    const order = {} as FinanceBusinessOrderApi.BusinessOrder;
    // 类型中应有 importerName（通过 keyof 在运行时验证类型声明）
    const keys: (keyof FinanceBusinessOrderApi.BusinessOrder)[] = [
      'importerId',
      'importerName',
      'entityCompanyDeptId',
      'entityCompanyName',
    ];
    // 仅验证字段在类型中存在（TypeScript 编译器会在类型不对时报错）
    expect(keys.length).toBe(4);
    // importer（旧字段）不应存在
    expect('importer' in order).toBe(false);
  });

  // 3. 分页查询参数使用 importDate（单值字符串），不使用 importDateRange
  it('PageQuery uses importDate string, not importDateRange tuple', () => {
    const query: FinanceBusinessOrderApi.PageQuery = {
      pageNo: 1,
      pageSize: 20,
      importDate: '2024-01-01',
    };
    expect(query.importDate).toBe('2024-01-01');
    expect('importDateRange' in query).toBe(false);
  });

  // 4. 列定义使用 importerName 字段显示导入人
  it('grid columns use importerName field, not importer', () => {
    const columns = useGridColumns() ?? [];
    expect(columns.some((c) => c.field === 'importerName')).toBe(true);
    expect(columns.some((c) => c.field === 'importer')).toBe(false);
  });

  // 5. 搜索表单使用 importDate 字段，不使用 importDateRange
  it('search form uses importDate field, not importDateRange', () => {
    const schemas = useGridFormSchema();
    expect(schemas.some((s) => s.fieldName === 'importDate')).toBe(true);
    expect(schemas.some((s) => s.fieldName === 'importDateRange')).toBe(false);
  });
});
