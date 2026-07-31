import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace FinanceCustomerCompanyApi {
  export interface CustomerCompany {
    id: number;
    code?: string;
    name: string;
    taxNo: string;
    bankName?: string;
    bankAccount?: string;
    address?: string;
    phone?: string;
    contactName?: string;
    email?: string;
    partyType?: string;
    /** 0 启用 1 停用 */
    status?: number;
    createTime?: string;
  }

  export interface PageQuery extends PageParam {
    name?: string;
    taxNo?: string;
    code?: string;
    status?: number;
  }

  export interface SaveForm {
    id?: number;
    name: string;
    taxNo: string;
    bankName?: string;
    bankAccount?: string;
    address?: string;
    phone?: string;
    contactName?: string;
    email?: string;
    status?: number;
  }
}

export function getCustomerCompanyPage(params: FinanceCustomerCompanyApi.PageQuery) {
  return requestClient.get<PageResult<FinanceCustomerCompanyApi.CustomerCompany>>(
    '/finance/customer-company/page',
    { params },
  );
}

export function getCustomerCompany(id: number) {
  return requestClient.get<FinanceCustomerCompanyApi.CustomerCompany>(
    `/finance/customer-company/get?id=${id}`,
  );
}

export function createCustomerCompany(data: FinanceCustomerCompanyApi.SaveForm) {
  return requestClient.post<number>('/finance/customer-company/create', data);
}

export function updateCustomerCompany(data: FinanceCustomerCompanyApi.SaveForm) {
  return requestClient.put<boolean>('/finance/customer-company/update', data);
}

export function updateCustomerCompanyStatus(id: number, status: number) {
  return requestClient.put<boolean>('/finance/customer-company/update-status', {
    id,
    status,
  });
}

/** 启用中的客户公司（开票选择） */
export function getCustomerCompanySimpleList() {
  return requestClient.get<FinanceCustomerCompanyApi.CustomerCompany[]>(
    '/finance/customer-company/simple-list',
  );
}
