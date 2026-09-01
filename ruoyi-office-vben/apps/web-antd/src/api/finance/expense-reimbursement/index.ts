import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace FinanceExpenseApi {
  export interface Line {
    lineKind: 'NORMAL' | 'PROXY';
    category: string;
    invoiceType?: string;
    subItem?: string;
    feeDate: string;
    amount: number;
    taxAmount?: number;
    attachments?: string[];
    invoiceFileUrl?: string;
    invoiceNo?: string;
    predocType?: string;
    predocProcessInstanceId?: string;
    predocBillId?: number;
    remark?: string;
    stayCityTier?: string;
    overLimitReason?: string;
  }

  export interface Bill {
    id: number;
    applicationNo?: string;
    processTitle?: string;
    periodLabel: string;
    payeeAccountName: string;
    payeeBankName?: string;
    payeeAccountNo: string;
    applyAmount: number;
    approvedAmount?: number;
    proxyTicket: boolean;
    status: string;
    processInstanceId?: string;
    /** 审批流程已结束（无流程实例视为已结束） */
    processEnded?: boolean;
    applicantUserId?: number;
    actualUserId?: number;
    entityCompanyName?: string;
    applicantDeptId?: number;
    applyDate?: string;
    financeComment?: string;
    actualPayDate?: string;
    companyBankAccountId?: number;
    extraAttachments?: string[];
    payVoucherUrl?: string;
    lines?: Line[];
  }

  export interface CreateForm {
    periodLabel: string;
    proxyTicket: boolean;
    actualUserId?: number;
    payeeAccountName: string;
    payeeBankName?: string;
    payeeAccountNo: string;
    extraAttachments?: string[];
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
    return requestClient.upload<{
      feeDate?: string;
      amount?: number;
      invoiceNo?: string;
      taxAmount?: number;
      invoiceType?: string;
      buyerName?: string;
      used?: boolean;
    }>(
      '/finance/expense-reimbursement/ocr-invoice',
      { file: raw },
    );
  }
  return requestClient.post<{
    feeDate?: string;
    amount?: number;
    invoiceNo?: string;
    taxAmount?: number;
    invoiceType?: string;
    buyerName?: string;
    used?: boolean;
  }>(
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
  payVoucherUrl?: string;
  payVoucherUrls?: string[];
  taskId?: string;
}) {
  return requestClient.put('/finance/expense-reimbursement/record-pay', data);
}
