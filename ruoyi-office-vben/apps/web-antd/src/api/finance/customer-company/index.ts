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
    isCustomer?: boolean;
    isSupplier?: boolean;
    /** 0 启用 1 停用 */
    status?: number;
    createTime?: string;
  }

  export interface PageQuery extends PageParam {
    name?: string;
    taxNo?: string;
    code?: string;
    status?: number;
    isCustomer?: boolean;
    isSupplier?: boolean;
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
    isCustomer?: boolean;
    isSupplier?: boolean;
    status?: number;
  }

  /** CUSTOMER=开票/合同；SUPPLIER=付款收款方 */
  export type SimpleListRole = 'CUSTOMER' | 'SUPPLIER';
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

/**
 * 启用中的客商精简列表。
 * @param role 默认 CUSTOMER（开票/合同兼容）；付款收款方传 SUPPLIER
 */
export function getCustomerCompanySimpleList(
  role: FinanceCustomerCompanyApi.SimpleListRole = 'CUSTOMER',
) {
  return requestClient.get<FinanceCustomerCompanyApi.CustomerCompany[]>(
    '/finance/customer-company/simple-list',
    { params: { role } },
  );
}

export interface CustomerCompanyImportResult {
  createdCodes: string[];
  failureRows: Record<number, string>;
}

export function importCustomerCompanyTemplate() {
  return requestClient.download(
    '/finance/customer-company/get-import-template',
  );
}

export function importCustomerCompany(file: File) {
  return requestClient.upload<CustomerCompanyImportResult>(
    '/finance/customer-company/import',
    { file },
  );
}
