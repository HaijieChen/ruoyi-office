import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';
import type { FinancePaymentApplicationApi } from '#/api/finance/payment-application';

export namespace FinanceTaxPaymentApi {
  export type Application = FinancePaymentApplicationApi.Application & {
    periodLabel?: string;
    applicationKind?: string;
    taxLines?: TaxLine[];
    paidLineSum?: number;
  };

  export interface TaxLine {
    entityCompanyDeptId: number;
    vatAmount?: number;
    surchargeAmount?: number;
    stampTaxAmount?: number;
    citAmount?: number;
    entityCompanyName?: string;
    lineTotal?: number;
  }

  export interface CreateRequest {
    paymentTiming: string;
    periodLabel: string;
    currency: string;
    specialNote?: string;
    evidenceFileUrls: string[];
    lines: TaxLine[];
    startUserSelectAssignees?: Record<string, number[]>;
  }

  export interface PageQuery extends PageParam {
    applicationNo?: string;
    status?: string;
  }
}

export function createAndStartTaxPayment(
  data: FinanceTaxPaymentApi.CreateRequest,
) {
  return requestClient.post<number>(
    '/finance/tax-payment/create-and-start',
    data,
  );
}

export function getTaxPayment(id: number) {
  return requestClient.get<FinanceTaxPaymentApi.Application>(
    `/finance/tax-payment/get?id=${id}`,
  );
}

export function getTaxPaymentPage(params: FinanceTaxPaymentApi.PageQuery) {
  return requestClient.get<PageResult<FinanceTaxPaymentApi.Application>>(
    '/finance/tax-payment/page',
    { params },
  );
}

export function recordPayTaxPayment(
  data: FinancePaymentApplicationApi.RecordPayRequest,
) {
  return requestClient.post<boolean>('/finance/tax-payment/record-pay', data);
}

export function resubmitTaxPayment(
  id: number,
  data: FinanceTaxPaymentApi.CreateRequest,
) {
  return requestClient.put<boolean>('/finance/tax-payment/resubmit', data, {
    params: { id },
  });
}
