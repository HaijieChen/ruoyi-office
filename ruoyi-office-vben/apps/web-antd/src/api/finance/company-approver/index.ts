import { requestClient } from '#/api/request';

export namespace FinanceCompanyApproverApi {
  export interface Item {
    entityCompanyDeptId: number;
    entityCompanyName?: string;
    userIds?: number[];
    userNames?: string[];
  }

  export interface SaveForm {
    entityCompanyDeptId: number;
    userIds: number[];
  }
}

export function getCompanyApproverList() {
  return requestClient.get<FinanceCompanyApproverApi.Item[]>(
    '/finance/company-approver/list',
  );
}

export function saveCompanyApprover(data: FinanceCompanyApproverApi.SaveForm) {
  return requestClient.put<boolean>('/finance/company-approver/save', data);
}
