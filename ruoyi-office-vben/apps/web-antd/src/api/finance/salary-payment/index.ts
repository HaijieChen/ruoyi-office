import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';
import type { FinancePaymentApplicationApi } from '#/api/finance/payment-application';

export namespace FinanceSalaryPaymentApi {
  export type Application = FinancePaymentApplicationApi.Application & {
    periodLabel?: string;
    applicationKind?: string;
    salaryLines?: SalaryLine[];
    paidLineSum?: number;
  };

  export interface SalaryLine {
    entityCompanyDeptId: number;
    netSalaryAmount: number;
    personalTaxAmount: number;
    socialInsuranceAmount: number;
    entityCompanyName?: string;
    lineTotal?: number;
  }

  export interface CreateRequest {
    paymentTiming: string;
    periodLabel: string;
    currency: string;
    specialNote?: string;
    evidenceFileUrls?: string[];
    lines: SalaryLine[];
    startUserSelectAssignees?: Record<string, number[]>;
  }

  export interface PageQuery extends PageParam {
    applicationNo?: string;
    status?: string;
  }
}

export function createAndStartSalaryPayment(
  data: FinanceSalaryPaymentApi.CreateRequest,
) {
  return requestClient.post<number>(
    '/finance/salary-payment/create-and-start',
    data,
  );
}

export function getSalaryPayment(id: number) {
  return requestClient.get<FinanceSalaryPaymentApi.Application>(
    `/finance/salary-payment/get?id=${id}`,
  );
}

export function getSalaryPaymentPage(params: FinanceSalaryPaymentApi.PageQuery) {
  return requestClient.get<PageResult<FinanceSalaryPaymentApi.Application>>(
    '/finance/salary-payment/page',
    { params },
  );
}

export function recordPaySalaryPayment(
  data: FinancePaymentApplicationApi.RecordPayRequest,
) {
  return requestClient.post<boolean>('/finance/salary-payment/record-pay', data);
}
