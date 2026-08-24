import { requestClient } from '#/api/request';

import type { FinanceInvoiceApplicationApi } from '#/api/finance/invoice-application';

export namespace FinanceInvoiceRedflushApi {
  export interface CreateRequest {
    predecessorApplicationId: number;
    reason: string;
    specialNote?: string;
    totalAmount?: number;
    startUserSelectAssignees?: Record<string, number[]>;
  }
}

export function listRedflushPredecessors() {
  return requestClient.get<FinanceInvoiceApplicationApi.Application[]>(
    '/finance/invoice-redflush/source-invoice-application-list',
  );
}

export function createAndStartInvoiceRedflush(
  data: FinanceInvoiceRedflushApi.CreateRequest,
) {
  return requestClient.post<number>(
    '/finance/invoice-redflush/create-and-start',
    data,
  );
}

export function completeIssueInvoiceRedflush(
  id: number,
  data: FinanceInvoiceApplicationApi.CompleteIssueRequest,
) {
  return requestClient.post<boolean>(
    '/finance/invoice-redflush/complete-issue',
    data,
    { params: { id } },
  );
}
