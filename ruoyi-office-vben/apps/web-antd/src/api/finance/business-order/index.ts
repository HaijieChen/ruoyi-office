import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace FinanceBusinessOrderApi {
  /** 业务订单状态：0=草稿 1=进行中 2=已关闭 */
  export type OrderStatus = 0 | 1 | 2;

  /** 业务订单 */
  export interface BusinessOrder {
    id: number;
    orderNo: string;
    businessSubject: string;
    businessType: string;
    contractRef?: string;
    projectRef?: string;
    receivableAmount: number;
    payableAmount: number;
    currency: string;
    ownerId: number;
    ownerName: string;
    status: OrderStatus;
    remark?: string;
    createTime: string;
  }

  /** 分页查询参数 */
  export interface PageQuery extends PageParam {
    orderNo?: string;
    businessSubject?: string;
    businessType?: string;
    contractRef?: string;
    projectRef?: string;
    status?: OrderStatus;
    currency?: string;
  }

  /** 创建/编辑表单数据 */
  export type SaveForm = Omit<BusinessOrder, 'id' | 'ownerName' | 'createTime'> & {
    id?: number;
  };
}

/** 查询业务订单分页 */
export function getBusinessOrderPage(params: FinanceBusinessOrderApi.PageQuery) {
  return requestClient.get<PageResult<FinanceBusinessOrderApi.BusinessOrder>>(
    '/finance/business-order/page',
    { params },
  );
}

/** 获取业务订单详情 */
export function getBusinessOrder(id: number) {
  return requestClient.get<FinanceBusinessOrderApi.BusinessOrder>(
    '/finance/business-order/get',
    { params: { id } },
  );
}

/** 创建业务订单 */
export function createBusinessOrder(data: FinanceBusinessOrderApi.SaveForm) {
  return requestClient.post<number>('/finance/business-order/create', data);
}

/** 更新业务订单 */
export function updateBusinessOrder(data: FinanceBusinessOrderApi.SaveForm) {
  return requestClient.put<void>('/finance/business-order/update', data);
}

/** 删除业务订单（仅草稿可删） */
export function deleteBusinessOrder(ids: number[]) {
  return requestClient.delete<void>('/finance/business-order/delete', {
    params: { ids: ids.join(',') },
  });
}
