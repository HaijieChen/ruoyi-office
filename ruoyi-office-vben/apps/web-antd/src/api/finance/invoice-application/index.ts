import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace FinanceInvoiceApplicationApi {
  export type ApprovalStatus =
    | 'PENDING'
    | 'APPROVED'
    | 'REJECTED'
    | 'CANCELLED';

  export interface Line {
    id?: number;
    businessOrderId: number;
    amount: number;
    invoiceCompany?: string;
    invoiceType?: string;
    billingPeriod?: string;
    sort?: number;
    issueStatus?: number;
    invoiceNo?: string;
    fileUrl?: string;
    issuedAt?: string;
  }

  export interface Application {
    id: number;
    applicationNo: string;
    processInstanceId?: string;
    approvalStatus: ApprovalStatus;
    issueStatus: number;
    totalAmount: number;
    confirmedClaimedAmount?: number;
    pendingClaimedAmount?: number;
    applicantUserId: number;
    buyerName?: string;
    buyerTaxNo?: string;
    invoiceCompany?: string;
    /** 开票公司组织部门 id */
    invoiceCompanyDeptId?: number;
    invoiceType?: string;
    voided?: boolean;
    createTime?: string;
    lines?: Line[];
  }

  export interface CreateAndStartRequest {
    expectedInvoiceDate?: string;
    invoiceCompany?: string;
    invoiceCompanyDeptId?: number;
    invoiceType?: string;
    buyerName: string;
    buyerTaxNo?: string;
    buyerAddressPhone?: string;
    buyerBankAccount?: string;
    specialInvoiceRequirement?: string;
    taxContent?: string;
    taxRate?: number;
    amountExcludingTax?: number;
    taxAmount?: number;
    evidenceFileUrl?: string;
    remark?: string;
    lines: Array<{
      businessOrderId: number;
      amount: number;
      invoiceCompany?: string;
      invoiceType?: string;
      billingPeriod?: string;
      sort?: number;
    }>;
  }

  export interface ResubmitRequest extends CreateAndStartRequest {
    id?: number;
  }

  export interface IssueProgressRequest {
    applicationId: number;
    lineId: number;
    invoiceNo: string;
    fileUrl?: string;
    issuedAt?: string;
  }

  export interface PageQuery extends PageParam {
    applicationNo?: string;
    approvalStatus?: ApprovalStatus | string;
    issueStatus?: number;
    buyerName?: string;
  }
}

export function createAndStartInvoiceApplication(
  data: FinanceInvoiceApplicationApi.CreateAndStartRequest,
) {
  return requestClient.post<number>(
    '/finance/invoice-application/create-and-start',
    data,
  );
}

export function resubmitInvoiceApplication(
  id: number,
  data: FinanceInvoiceApplicationApi.ResubmitRequest,
) {
  return requestClient.put<boolean>(
    '/finance/invoice-application/resubmit',
    data,
    { params: { id } },
  );
}

export function updateInvoiceIssueProgress(
  data: FinanceInvoiceApplicationApi.IssueProgressRequest,
) {
  return requestClient.put<boolean>(
    '/finance/invoice-application/update-issue-progress',
    data,
  );
}

export function getInvoiceApplication(id: number) {
  return requestClient.get<FinanceInvoiceApplicationApi.Application>(
    '/finance/invoice-application/get',
    { params: { id } },
  );
}

export function getInvoiceApplicationPage(
  params: FinanceInvoiceApplicationApi.PageQuery,
) {
  return requestClient.get<
    PageResult<FinanceInvoiceApplicationApi.Application>
  >('/finance/invoice-application/page', { params });
}
