import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace FinanceExpenseApi {
  export interface Line {
    lineKind: 'NORMAL' | 'PROXY';
    category: string;
    feeDate: string;
    amount: number;
    attachments?: string[];
    invoiceFileUrl?: string;
    predocType?: string;
    predocProcessInstanceId?: string;
    remark?: string;
    stayCityTier?: string;
    overLimitReason?: string;
  }

  export interface Bill {
    id: number;
    processTitle?: string;
    periodLabel: string;
    payeeAccountName: string;
    payeeAccountNo: string;
    applyAmount: number;
    approvedAmount?: number;
    proxyTicket: boolean;
    status: string;
    processInstanceId?: string;
    applicantUserId?: number;
    applicantDeptId?: number;
    applyDate?: string;
    financeComment?: string;
    actualPayDate?: string;
    companyBankAccountId?: number;
    lines?: Line[];
  }

  export interface CreateForm {
    periodLabel: string;
    proxyTicket: boolean;
    payeeAccountName: string;
    payeeAccountNo: string;
    lines: Line[];
  }

  export interface PageQuery extends PageParam {
    status?: string;
    periodLabel?: string;
  }
}

export function createExpenseReimbursement(data: FinanceExpenseApi.CreateForm) {
  return requestClient.post<number>('/finance/expense-reimbursement/create', data);
}

export function createNoInvoiceExpense(data: FinanceExpenseApi.CreateForm) {
  return requestClient.post<number>(
    '/finance/expense-reimbursement/create-no-invoice',
    data,
  );
}

export function ocrExpenseInvoice(fileUrl: string, file?: File) {
  const raw =
    file && (file as any).originFileObj instanceof Blob
      ? (file as any).originFileObj
      : file;
  if (raw instanceof Blob) {
    return requestClient.upload<{ feeDate?: string; amount?: number }>(
      '/finance/expense-reimbursement/ocr-invoice',
      { file: raw },
    );
  }
  return requestClient.post<{ feeDate?: string; amount?: number }>(
    '/finance/expense-reimbursement/ocr-invoice',
    null,
    { params: { fileUrl } },
  );
}

export function getExpenseReimbursement(id: number) {
  return requestClient.get<FinanceExpenseApi.Bill>(
    '/finance/expense-reimbursement/get',
    { params: { id } },
  );
}

export function getExpenseReimbursementPage(
  params: FinanceExpenseApi.PageQuery,
) {
  return requestClient.get<PageResult<FinanceExpenseApi.Bill>>(
    '/finance/expense-reimbursement/page',
    { params },
  );
}

export function approveExpenseReimbursement(data: {
  id: number;
  approvedAmount: number;
  financeComment?: string;
  taskId?: string;
}) {
  return requestClient.put('/finance/expense-reimbursement/approve', data);
}

export function recordPayExpenseReimbursement(data: {
  id: number;
  companyBankAccountId: number;
  actualPayDate: string;
  payVoucherUrl: string;
  taskId?: string;
}) {
  return requestClient.put('/finance/expense-reimbursement/record-pay', data);
}
