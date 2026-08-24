import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace FinanceCompanyBankAccountApi {
  export interface Account {
    id: number;
    entityCompanyDeptId: number;
    entityCompanyName?: string;
    accountName: string;
    bankName: string;
    accountHolder: string;
    accountNo?: string;
    accountNoMasked?: string;
    accountType?: string;
    currency?: string;
    status?: number;
    remark?: string;
    createTime?: string;
  }

  export interface PageQuery extends PageParam {
    entityCompanyDeptId?: number;
    accountName?: string;
    bankName?: string;
    accountNo?: string;
    status?: number;
    currency?: string;
  }

  export interface SaveForm {
    id?: number;
    entityCompanyDeptId: number;
    accountName: string;
    bankName: string;
    accountHolder: string;
    accountNo: string;
    accountType?: string;
    currency: string;
    status?: number;
    remark?: string;
  }
}

export function getCompanyBankAccountPage(
  params: FinanceCompanyBankAccountApi.PageQuery,
) {
  return requestClient.get<PageResult<FinanceCompanyBankAccountApi.Account>>(
    '/finance/company-bank-account/page',
    { params },
  );
}

export function getCompanyBankAccount(id: number) {
  return requestClient.get<FinanceCompanyBankAccountApi.Account>(
    `/finance/company-bank-account/get?id=${id}`,
  );
}

export function createCompanyBankAccount(
  data: FinanceCompanyBankAccountApi.SaveForm,
) {
  return requestClient.post<number>('/finance/company-bank-account/create', data);
}

export function updateCompanyBankAccount(
  data: FinanceCompanyBankAccountApi.SaveForm,
) {
  return requestClient.put<boolean>('/finance/company-bank-account/update', data);
}

export function updateCompanyBankAccountStatus(id: number, status: number) {
  return requestClient.put<boolean>(
    '/finance/company-bank-account/update-status',
    { id, status },
  );
}

/** 出纳选账户：按主体公司过滤，账号脱敏 */
export function importCompanyBankAccountTemplate() {
  return requestClient.download('/finance/company-bank-account/get-import-template');
}

export function importCompanyBankAccount(file: File) {
  return requestClient.upload<{
    createdNos: string[];
    failureRows: Record<number, string>;
  }>('/finance/company-bank-account/import', { file });
}

export function getCompanyBankAccountSimpleList(entityCompanyDeptId: number) {
  return requestClient.get<FinanceCompanyBankAccountApi.Account[]>(
    '/finance/company-bank-account/simple-list',
    { params: { entityCompanyDeptId } },
  );
}
