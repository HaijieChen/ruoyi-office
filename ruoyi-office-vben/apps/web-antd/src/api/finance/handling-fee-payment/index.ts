import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace FinanceHandlingFeePaymentApi {
  export interface Record {
    id: number;
    feeDate: string;
    amount: number;
    currency: string;
    entityCompanyDeptId: number;
    entityCompanyName?: string;
    companyBankAccountId: number;
    accountName?: string;
    bankName?: string;
    accountNo?: string;
    accountNoMasked?: string;
    createTime?: string;
  }

  export interface PageQuery extends PageParam {
    feeDate?: string[];
    entityCompanyDeptId?: number;
  }

  export interface SaveForm {
    id?: number;
    feeDate: string;
    amount: number;
    currency: string;
    entityCompanyDeptId: number;
    companyBankAccountId: number;
  }

  export interface ImportResult {
    createdIds: number[];
    failureRows: Record<number, string>;
  }
}

export function getHandlingFeePaymentPage(
  params: FinanceHandlingFeePaymentApi.PageQuery,
) {
  return requestClient.get<PageResult<FinanceHandlingFeePaymentApi.Record>>(
    '/finance/handling-fee-payment/page',
    { params },
  );
}

export function getHandlingFeePayment(id: number) {
  return requestClient.get<FinanceHandlingFeePaymentApi.Record>(
    `/finance/handling-fee-payment/get?id=${id}`,
  );
}

export function createHandlingFeePayment(
  data: FinanceHandlingFeePaymentApi.SaveForm,
) {
  return requestClient.post<number>('/finance/handling-fee-payment/create', data);
}

export function updateHandlingFeePayment(
  data: FinanceHandlingFeePaymentApi.SaveForm,
) {
  return requestClient.put<boolean>('/finance/handling-fee-payment/update', data);
}

export function deleteHandlingFeePayment(id: number) {
  return requestClient.delete<boolean>('/finance/handling-fee-payment/delete', {
    params: { id },
  });
}

export function importHandlingFeePaymentTemplate() {
  return requestClient.download(
    '/finance/handling-fee-payment/get-import-template',
  );
}

export function importHandlingFeePayment(file: File) {
  return requestClient.upload<FinanceHandlingFeePaymentApi.ImportResult>(
    '/finance/handling-fee-payment/import',
    { file },
  );
}
