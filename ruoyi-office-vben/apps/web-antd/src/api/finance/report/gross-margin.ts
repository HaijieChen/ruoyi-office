import { requestClient } from '#/api/request';

export namespace FinanceGrossMarginApi {
  export interface Row {
    yearMonth: string;
    deptId: number;
    deptName?: string;
    productType: string;
    incomeAmount: number;
    costAmount: number;
    marginAmount: number;
  }
  export interface PageQuery {
    pageNo?: number;
    pageSize?: number;
    fromMonth?: string;
    toMonth?: string;
    deptId?: number;
    productType?: string;
  }
  export interface PageResult {
    list: Row[];
    total: number;
    excludedNonCnyCount: number;
  }
}

export function getGrossMarginPage(params: FinanceGrossMarginApi.PageQuery) {
  return requestClient.get<FinanceGrossMarginApi.PageResult>(
    '/finance/report/gross-margin/page',
    { params },
  );
}

export function exportGrossMarginExcel(params: FinanceGrossMarginApi.PageQuery) {
  return requestClient.download('/finance/report/gross-margin/export-excel', {
    params,
  });
}
