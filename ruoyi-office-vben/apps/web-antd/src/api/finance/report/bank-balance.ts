import { requestClient } from '#/api/request';

export namespace FinanceBankBalanceApi {
  export interface Row {
    accountId: number;
    accountName?: string;
    accountNoMasked?: string;
    entityCompanyDeptId?: number;
    openingAmount: number;
    openingAsOfDate?: string;
    incomeAmount: number;
    payExpenseAmount: number;
    reimbursementExpenseAmount: number;
    balanceAmount: number;
    asOf?: string;
  }
  export interface Result {
    list: Row[];
    excludedFxCount: number;
    unmatchedReceiptCount: number;
  }
}

export function getBankBalanceList(params?: { asOf?: string }) {
  return requestClient.get<FinanceBankBalanceApi.Result>(
    '/finance/report/bank-balance/list',
    { params },
  );
}
