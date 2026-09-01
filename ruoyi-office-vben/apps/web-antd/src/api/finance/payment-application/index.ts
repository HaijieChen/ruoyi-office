import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace FinancePaymentApplicationApi {
  export type Status =
    | 'PENDING'
    | 'WAIT_PAY'
    | 'PARTIAL_PAID'
    | 'PAID'
    | 'REJECTED'
    | 'CANCELLED';

  export interface Application {
    id: number;
    applicationNo: string;
    processInstanceId?: string;
    status: Status;
    currentNodeKey?: string;
    currentNodeName?: string;
    processTitle?: string;
    applicantUserId?: number;
    businessStaffUserId?: number;
    applicantDeptId?: number;
    applyDate?: string;
    paymentTiming: string;
    paymentReason: string;
    purchaseProcessInstanceId?: string;
    purchaseSnapshot?: string;
    leaseContractApplicationId?: number;
    relatedContractApplicationId?: number;
    payeeCompanyId: number;
    payeeName: string;
    payeeBankName: string;
    payeeBankAccount: string;
    /** 主体公司组织 ID */
    entityCompanyDeptId?: number;
    /** 主体公司名称快照；历史空值前端展示「历史未记录」 */
    entityCompanyName?: string;
    applyAmount: number;
    currency?: string;
    businessSettlementTerm: string;
    contractSettlementMethod?: string;
    payMethod?: string;
    costProject?: string;
    accountingSubject?: string;
    evidenceFileUrls?: string;
    specialNote?: string;
    actualPayDate?: string;
    payVoucherUrl?: string;
    erpVoucherNo?: string;
    paidLineSum?: number;
    payLines?: Array<{
      payAmount?: number;
      actualPayDate?: string;
      payVoucherUrl?: string;
    }>;
    cumulativePaid?: number;
    cumulativeAfter?: number;
    createTime?: string;
  }

  export interface CreateAndStartRequest {
    paymentTiming: string;
    paymentReason: string;
    purchaseProcessInstanceId?: string;
    leaseContractApplicationId?: number;
    relatedContractApplicationId?: number;
    payeeCompanyId: number;
    payeeBankName?: string;
    payeeBankAccount?: string;
    /** 主体公司（启用公司下拉） */
    entityCompanyDeptId: number;
    applyAmount: number;
    /** 交易币种 CNY/USD/HKD */
    currency: string;
    businessSettlementTerm: string;
    payMethod?: string;
    costProject?: string;
    evidenceFileUrls: string[];
    specialNote?: string;
    applicantDeptId?: number;
    businessStaffUserId?: number;
    /** 发起人自选节点审批人 activityId -> userIds */
    startUserSelectAssignees?: Record<string, number[]>;
  }

  export type ResubmitRequest = CreateAndStartRequest;

  export interface PageQuery extends PageParam {
    applicationNo?: string;
    status?: Status | string;
    paymentReason?: string;
    payeeCompanyId?: number;
    payeeName?: string;
    entityCompanyDeptId?: number;
  }

  export interface PurchaseInstance {
    processInstanceId: string;
    processDefinitionKey?: string;
    name?: string;
    startUserId?: number;
    startTime?: string;
    endTime?: string;
    summary?: string;
  }

  export interface RecordPayLine {
    companyBankAccountId: number;
    payAmount: number;
    actualPayDate: string;
    payVoucherUrl: string;
    erpVoucherNo?: string;
    idempotencyKey: string;
  }

  export interface RecordPayRequest {
    id: number;
    taskId?: string;
    /** 公司银行账户 id；单行提交时必填 */
    companyBankAccountId?: number;
    /** 本笔金额；缺省按剩余未付 */
    payAmount?: number;
    actualPayDate?: string;
    payVoucherUrl?: string;
    erpVoucherNo?: string;
    idempotencyKey?: string;
    materialsComplete?: boolean;
    lines?: RecordPayLine[];
  }
}

export function createAndStartPaymentApplication(
  data: FinancePaymentApplicationApi.CreateAndStartRequest,
) {
  return requestClient.post<number>(
    '/finance/payment-application/create-and-start',
    data,
  );
}

export function resubmitPaymentApplication(
  id: number,
  data: FinancePaymentApplicationApi.ResubmitRequest,
) {
  return requestClient.put<boolean>(
    '/finance/payment-application/resubmit',
    data,
    { params: { id } },
  );
}

export function getPaymentApplication(id: number) {
  return requestClient.get<FinancePaymentApplicationApi.Application>(
    `/finance/payment-application/get?id=${id}`,
  );
}

export function getPaymentApplicationPage(
  params: FinancePaymentApplicationApi.PageQuery,
) {
  return requestClient.get<PageResult<FinancePaymentApplicationApi.Application>>(
    '/finance/payment-application/page',
    { params },
  );
}

export function getCumulativePaid(payeeCompanyId: number) {
  return requestClient.get<{ paidSum: number }>(
    '/finance/payment-application/cumulative-paid',
    { params: { payeeCompanyId } },
  );
}

export function listSelectablePurchaseInstances() {
  return requestClient.get<FinancePaymentApplicationApi.PurchaseInstance[]>(
    '/finance/payment-application/list-selectable-purchase-instances',
  );
}

export function listSelectableLeaseContracts() {
  return requestClient.get(
    '/finance/contract-application/list-selectable-for-lease-payment',
  );
}

export function ocrPaymentVoucher(fileUrl: string, file?: File) {
  const raw =
    file && (file as any).originFileObj instanceof Blob
      ? (file as any).originFileObj
      : file;
  if (raw instanceof Blob) {
    return requestClient.upload<{
      amount?: number;
      feeDate?: string;
      invoiceNo?: string;
    }>(
      '/finance/payment-application/ocr-voucher',
      { file: raw },
    );
  }
  return requestClient.post<{
    amount?: number;
    feeDate?: string;
    invoiceNo?: string;
  }>(
    '/finance/payment-application/ocr-voucher',
    null,
    { params: { fileUrl } },
  );
}

export function recordPayPaymentApplication(
  data: FinancePaymentApplicationApi.RecordPayRequest,
) {
  return requestClient.post<boolean>(
    '/finance/payment-application/record-pay',
    data,
  );
}

export function confirmPaymentMaterials(id: number, taskId: string) {
  return requestClient.post<boolean>('/finance/payment-application/confirm-materials', null, {
    params: { id, taskId },
  });
}

/** 财务主管节点写入费用科目/性质（F4） */
export function updatePaymentAccountingSubject(
  id: number,
  taskId: string,
  accountingSubject: string,
) {
  return requestClient.put<boolean>(
    '/finance/payment-application/update-accounting-subject',
    null,
    { params: { id, taskId, accountingSubject } },
  );
}
