import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace FinanceContractApplicationApi {
  export type ApprovalStatus =
    | 'PENDING'
    | 'APPROVED'
    | 'REJECTED'
    | 'CANCELLED';

  export interface Application {
    id: number;
    applicationNo: string;
    processInstanceId?: string;
    approvalStatus: ApprovalStatus;
    currentNodeKey?: string;
    currentNodeName?: string;
    applicantUserId?: number;
    businessStaffUserId?: number;
    applicantDeptId?: number;
    counterpartyCompanyId?: number;
    counterpartyName?: string;
    amountNa?: boolean;
    contractAmount?: number;
    entityCompanyDeptId?: number;
    entityCompanyName?: string;
    currency?: string;
    signCompany?: string;
    fileName?: string;
    fileType?: string;
    productType?: string;
    rebateRatio?: string;
    settlementMethod?: string;
    copyCount?: number;
    sealTypes?: string;
    needMail?: boolean;
    mailAddress?: string;
    preProcessRef?: string;
    startDate?: string;
    endDate?: string;
    draftFileUrl?: string;
    sealFileUrl?: string;
    actualSealerUserId?: number;
    archivedAt?: string;
    mailTrackingNo?: string;
    remark?: string;
    voided?: boolean;
    createTime?: string;
  }

  export interface CreateAndStartRequest {
    counterpartyCompanyId: number;
    amountNa?: boolean;
    contractAmount?: number;
    entityCompanyDeptId: number;
    currency?: string;
    signCompany?: string;
    fileName: string;
    fileType: string;
    productType?: string;
    rebateRatio?: string;
    settlementMethod?: string;
    copyCount: number;
    sealTypes: string;
    needMail: boolean;
    mailAddress?: string;
    preProcessRef?: string;
    startDate?: string;
    endDate?: string;
    draftFileUrl: string;
    remark?: string;
    applicantDeptId?: number;
    businessStaffUserId?: number;
    /** 发起人自选节点审批人 activityId -> userIds */
    startUserSelectAssignees?: Record<string, number[]>;
    startCompanyDeptId?: number;
  }

  export type ResubmitRequest = CreateAndStartRequest;

  export interface PageQuery extends PageParam {
    applicationNo?: string;
    approvalStatus?: ApprovalStatus | string;
    applicantUserId?: number;
    signCompany?: string;
    fileType?: string;
    productType?: string;
    counterpartyName?: string;
  }
}

export function createAndStartContractApplication(
  data: FinanceContractApplicationApi.CreateAndStartRequest,
) {
  return requestClient.post<number>(
    '/finance/contract-application/create-and-start',
    data,
  );
}

export function resubmitContractApplication(
  id: number,
  data: FinanceContractApplicationApi.ResubmitRequest,
) {
  return requestClient.put<boolean>(
    '/finance/contract-application/resubmit',
    data,
    { params: { id } },
  );
}

export function cancelContractApplication(id: number) {
  return requestClient.post<boolean>(
    '/finance/contract-application/cancel',
    null,
    { params: { id } },
  );
}

export function getContractApplication(id: number) {
  return requestClient.get<FinanceContractApplicationApi.Application>(
    '/finance/contract-application/get',
    { params: { id } },
  );
}

export function getContractApplicationPage(
  params: FinanceContractApplicationApi.PageQuery,
) {
  return requestClient.get<
    PageResult<FinanceContractApplicationApi.Application>
  >('/finance/contract-application/page', { params });
}

/** 商务签单可选合同（已通过且本人申请） */
export function listSelectableContractsForBo() {
  return requestClient.get<FinanceContractApplicationApi.Application[]>(
    '/finance/contract-application/list-selectable-for-bo',
  );
}

/** 用印登记并 complete 任务（CS-F2：须传 taskId） */
export function recordContractSeal(id: number, taskId: string, sealFileUrl: string) {
  return requestClient.post<boolean>(
    '/finance/contract-application/record-seal',
    null,
    { params: { id, taskId, sealFileUrl } },
  );
}

export function recordContractArchive(id: number, taskId: string) {
  return requestClient.post<boolean>(
    '/finance/contract-application/record-archive',
    null,
    { params: { id, taskId } },
  );
}

export function recordContractMail(
  id: number,
  taskId: string,
  mailTrackingNo: string,
) {
  return requestClient.post<boolean>(
    '/finance/contract-application/record-mail',
    null,
    { params: { id, taskId, mailTrackingNo } },
  );
}

export interface ContractApplicationImportResult {
  createdNos: string[];
  failureRows: Record<number, string>;
}

export function importContractApplicationTemplate() {
  return requestClient.download(
    '/finance/contract-application/get-import-template',
  );
}

export function importContractApplication(file: File) {
  return requestClient.upload<ContractApplicationImportResult>(
    '/finance/contract-application/import',
    { file },
  );
}
