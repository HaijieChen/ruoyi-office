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
    businessOrderId?: number;
    /** 商务单号（标识） */
    businessOrderNo?: string;
    /** 来源合同签约申请 id（行级历史快照，空=未证实） */
    sourceContractApplicationId?: number;
    /** 来源合同业务单号（仅由行级 source 解析） */
    contractApplicationNo?: string;
    /** 产品类型快照（行级历史，空=未证实） */
    productType?: string;
    historyProductUnproven?: boolean;
    historySourceContractUnproven?: boolean;
    /** 当前态（非历史） */
    currentContractApplicationId?: number;
    currentContractApplicationNo?: string;
    currentProductType?: string;
    amount: number;
    invoiceCompany?: string;
    invoiceType?: string;
    billingPeriod?: string;
    remark?: string;
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
    /** 服务端可认领金额 */
    claimableAmount?: number;
    applicantUserId: number;
    businessStaffUserId?: number;
    buyerName?: string;
    buyerTaxNo?: string;
    buyerAddressPhone?: string;
    buyerBankAccount?: string;
    /** 弱关联客户公司 */
    customerCompanyId?: number;
    specialInvoiceRequirement?: string;
    /** 产品类型（服务端字段 taxContent） */
    taxContent?: string;
    remark?: string;
    invoiceCompany?: string;
    /** 开票公司组织部门 id */
    invoiceCompanyDeptId?: number;
    currency?: string;
    invoiceType?: string;
    voided?: boolean;
    createTime?: string;
    lines?: Line[];
    files?: IssueFile[];
  }

  export interface CreateAndStartRequest {
    expectedInvoiceDate?: string;
    invoiceCompany?: string;
    invoiceCompanyDeptId?: number;
    currency?: string;
    invoiceType?: string;
    /** 必选启用客户公司；服务端写 buyer 快照 */
    customerCompanyId: number;
    buyerName?: string;
    buyerTaxNo?: string;
    buyerAddressPhone?: string;
    buyerBankAccount?: string;
    /** 特别开票要求（单据级） */
    specialInvoiceRequirement?: string;
    /** 产品类型（字典 finance_product_type；品牌商务 = ppsw） */
    taxContent: string;
    taxRate?: number;
    amountExcludingTax?: number;
    taxAmount?: number;
    evidenceFileUrl?: string;
    remark?: string;
    businessStaffUserId?: number;
    lines: Array<{
      businessOrderId?: number;
      sourceContractApplicationId?: number;
      amount: number;
      invoiceCompany?: string;
      invoiceType?: string;
      billingPeriod?: string;
      remark?: string;
      sort?: number;
    }>;
    /** 发起人自选节点审批人 activityId -> userIds */
    startUserSelectAssignees?: Record<string, number[]>;
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

  export interface IssueFile {
    id?: number;
    applicationId?: number;
    fileUrl?: string;
    fileName?: string;
    sort?: number;
  }

  export interface CompleteIssueRequest {
    applicationId: number;
    invoiceNos?: string[];
    files: Array<{ url: string; name?: string }>;
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

/** @deprecated 请用 completeInvoiceIssue */
export function updateInvoiceIssueProgress(
  data: FinanceInvoiceApplicationApi.IssueProgressRequest,
) {
  return requestClient.put<boolean>(
    '/finance/invoice-application/update-issue-progress',
    data,
  );
}

/** 整单办票（多附件 replace） */
export function completeInvoiceIssue(
  data: FinanceInvoiceApplicationApi.CompleteIssueRequest,
) {
  return requestClient.put<boolean>(
    '/finance/invoice-application/complete-issue',
    data,
  );
}

export function getInvoiceApplication(id: number) {
  return requestClient.get<FinanceInvoiceApplicationApi.Application>(
    '/finance/invoice-application/get',
    { params: { id } },
  );
}

export function importInvoiceApplicationTemplate() {
  return requestClient.download('/finance/invoice-application/get-import-template');
}

export function importInvoiceApplication(file: File) {
  return requestClient.upload<{
    createdNos: string[];
    failureRows: Record<number, string>;
  }>('/finance/invoice-application/import', { file });
}

export function getInvoiceApplicationPage(
  params: FinanceInvoiceApplicationApi.PageQuery,
) {
  return requestClient.get<
    PageResult<FinanceInvoiceApplicationApi.Application>
  >('/finance/invoice-application/page', { params });
}
