import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace FinanceReceiptClaimApi {
  /** 认领单状态：0=待确认 1=已通过 2=已驳回 3=已撤销 */
  export type ClaimStatus = 0 | 1 | 2 | 3;

  /** 认领单明细项 */
  export interface ClaimItem {
    id: number;
    receiptId: number;
    receiptNo: string;
    payerName: string;
    bankSerialNo: string;
    /** 新链路：开票申请 */
    invoiceApplicationId?: number;
    invoiceApplicationNo?: string;
    claimSource?: 'INVOICE' | 'LEGACY_BO';
    /** 历史 LEGACY */
    businessOrderId?: number;
    businessOrderNo?: string;
    businessSubject?: string;
    productName?: string;
    claimAmount: number;
  }

  /** 认领单（含明细） */
  export interface ReceiptClaim {
    id: number;
    claimantId: number;
    status: ClaimStatus;
    totalClaimAmount: number;
    remark?: string;
    rejectReason?: string;
    reviewerId?: number;
    reviewTime?: string;
    createTime: string;
    items: ClaimItem[];
  }

  /** 分页查询参数（my-page 和 review-page 共用） */
  export interface ClaimPageQuery extends PageParam {
    status?: ClaimStatus;
    claimantId?: number;
    /** 创建时间范围 [startDateTime, endDateTime] */
    createTime?: [string, string];
  }

  /** 保存请求（新建/修改共用；新链路挂开票申请） */
  export interface SaveRequest {
    /** 修改时必填 */
    id?: number;
    remark?: string;
    items: Array<{
      receiptId: number;
      invoiceApplicationId: number;
      claimAmount: number;
    }>;
  }

  /** 驳回请求体 */
  export interface RejectRequest {
    id: number;
    reason: string;
  }

  /** 撤销请求体 */
  export interface RevokeRequest {
    id: number;
    reason: string;
  }

  /** 审计历史记录（后端撤销审计 + 展示兼容字段） */
  export interface AuditLog {
    id: number;
    claimId: number;
    action?: string;
    operatorId?: number;
    operatorName?: string;
    reviewerId?: number;
    reviewerName?: string;
    reason?: string;
    revokeReason?: string;
    createTime?: string;
    revokeTime?: string;
  }
}

/** 创建认领单 */
export function createClaim(data: FinanceReceiptClaimApi.SaveRequest) {
  return requestClient.post<number>('/finance/receipt-claim/create', data);
}

/** 修改认领单（本人待确认或已驳回） */
export function updateClaim(data: FinanceReceiptClaimApi.SaveRequest) {
  return requestClient.put<boolean>('/finance/receipt-claim/update', data);
}

/** 我的认领单分页 */
export function getMyClaimPage(params: FinanceReceiptClaimApi.ClaimPageQuery) {
  return requestClient.get<PageResult<FinanceReceiptClaimApi.ReceiptClaim>>(
    '/finance/receipt-claim/my-page',
    { params },
  );
}

/** 财务复核分页 */
export function getReviewPage(params: FinanceReceiptClaimApi.ClaimPageQuery) {
  return requestClient.get<PageResult<FinanceReceiptClaimApi.ReceiptClaim>>(
    '/finance/receipt-claim/review-page',
    { params },
  );
}

/** 本人认领单详情 */
export function getMyClaim(id: number) {
  return requestClient.get<FinanceReceiptClaimApi.ReceiptClaim>(
    '/finance/receipt-claim/get',
    { params: { id } },
  );
}

/** 财务复核详情 */
export function getReviewClaim(id: number) {
  return requestClient.get<FinanceReceiptClaimApi.ReceiptClaim>(
    '/finance/receipt-claim/review-get',
    { params: { id } },
  );
}

/** 确认认领单（PUT /confirm?id=） */
export function confirmClaim(id: number) {
  return requestClient.put<boolean>(
    '/finance/receipt-claim/confirm',
    undefined,
    { params: { id } },
  );
}

/** 驳回认领单（PUT /reject body {id, reason}） */
export function rejectClaim(data: FinanceReceiptClaimApi.RejectRequest) {
  return requestClient.put<boolean>('/finance/receipt-claim/reject', data);
}

/** 重新提交已驳回的认领单（PUT /resubmit?id=） */
export function resubmitClaim(id: number) {
  return requestClient.put<boolean>(
    '/finance/receipt-claim/resubmit',
    undefined,
    { params: { id } },
  );
}

/** 撤销已确认认领单（PUT /revoke body {id, reason}） */
export function revokeClaim(data: FinanceReceiptClaimApi.RevokeRequest) {
  return requestClient.put<boolean>('/finance/receipt-claim/revoke', data);
}

/** 查询认领单撤销审计历史（GET /revoke-audit-list?claimId=） */
export function getRevokeAuditList(claimId: number) {
  return requestClient.get<FinanceReceiptClaimApi.AuditLog[]>(
    '/finance/receipt-claim/revoke-audit-list',
    { params: { claimId } },
  );
}

/** 可认领银行到款分页（创建认领时选源） */
export function getSourceReceiptPage(params: PageParam) {
  return requestClient.get<PageResult<Record<string, any>>>(
    '/finance/receipt-claim/source-receipt-page',
    { params },
  );
}

/** 可认领商务单分页（历史只读；新写禁止 LEGACY） */
export function getSourceBusinessOrderPage(params: PageParam) {
  return requestClient.get<PageResult<Record<string, any>>>(
    '/finance/receipt-claim/source-business-order-page',
    { params },
  );
}

/** 可认领开票申请分页（批过未出票可选） */
export function getSourceInvoiceApplicationPage(params: PageParam) {
  return requestClient.get<PageResult<Record<string, any>>>(
    '/finance/receipt-claim/source-invoice-application-page',
    { params },
  );
}
