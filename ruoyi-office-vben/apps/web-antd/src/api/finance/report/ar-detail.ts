import { requestClient } from '#/api/request';

export namespace FinanceArDetailApi {
  export interface Row {
    id: number;
    orderNo: string;
    entityCompanyDeptId?: number;
    entityCompanyName?: string;
    productType?: string;
    settlementAmount: number;
    invoicedOccupiedAmount: number;
    confirmedClaimedAmount: number;
    uninvoicedAmount: number;
    invoicedArAmount: number;
    arTotalAmount: number;
    currency: string;
  }

  export interface PageQuery {
    pageNo?: number;
    pageSize?: number;
    entityCompanyDeptId?: number;
    productType?: string;
    uninvoicedOnly?: boolean;
    invoicedArOnly?: boolean;
  }

  export interface PageResult {
    list: Row[];
    total: number;
    excludedNonCnyCount: number;
  }
}

export function getArDetailPage(params: FinanceArDetailApi.PageQuery) {
  return requestClient.get<FinanceArDetailApi.PageResult>(
    '/finance/report/ar-detail/page',
    { params },
  );
}

export function exportArDetailExcel(params: FinanceArDetailApi.PageQuery) {
  return requestClient.download('/finance/report/ar-detail/export-excel', {
    params,
  });
}
