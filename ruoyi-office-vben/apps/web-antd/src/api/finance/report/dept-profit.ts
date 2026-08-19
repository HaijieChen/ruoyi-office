import { requestClient } from '#/api/request';

export namespace FinanceDeptProfitApi {
  export interface Row {
    deptId?: number;
    deptName?: string;
    incomeAmount: number;
    costAmount: number;
    reimbursementAmount: number;
    allocationAmount: number;
    profitAmount: number;
    unallocated?: boolean;
    totalRow?: boolean;
  }
  export interface Result {
    list: Row[];
    total?: Row;
    excludedNonCnyCount: number;
    salaryTaxPaidAmount: number;
    salaryAllocationAmount: number;
    unallocatedResidualAmount: number;
  }
}

export function getDeptProfitList(params?: { fromMonth?: string; toMonth?: string }) {
  return requestClient.get<FinanceDeptProfitApi.Result>(
    '/finance/report/dept-profit/list',
    { params },
  );
}
