import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace FinanceBusinessOrderApi {
  /** 签单记录（对应 OA表单-722.xlsx 工作簿字段） */
  export interface BusinessOrder {
    /** 系统生成，只读 */
    id: number;
    /** 系统生成，只读 */
    orderNo: string;
    /** 系统生成，只读 */
    importDate: string;
    /** 系统生成，只读 */
    importerId: number;
    /** 系统生成，只读 */
    importerName: string;
    /** 主体公司组织部门编号 */
    entityCompanyDeptId: number;
    /** 主体公司名称快照 */
    entityCompanyName?: string;
    currency?: string;
    /** 可选：流程合同ID（legacy，列表不再主展示） */
    contractProcessId?: string;
    /** 合同签约申请编号（正式关联） */
    contractApplicationId?: number;
    /** 合同业务单号（详情/列表展示） */
    contractApplicationNo?: string;
    /** 签单日期 */
    orderDate: string;
    /** 产品/服务（legacy 兼容读取） */
    productName?: string;
    /** 产品类型快照（权威；开票可选仅认此字段） */
    productTypeSnapshot?: string;
    /** 产品类型（规范展示字段，优先快照 dual-read；EXP-70） */
    productType?: string;
    /** 对接人 */
    contactPerson: string;
    /** 执行开始日期 */
    executionStartDate: string;
    /** 执行结束日期 */
    executionEndDate: string;
    /** 付款方（可选） */
    payerName?: string;
    /** 签约执行金额 */
    signedExecutionAmount: number;
    /** 折扣率（为空时视为0） */
    discountRate?: number;
    /** 结算金额，后端 HALF_UP 计算，只读 */
    settlementAmount: number;
    /** 交易币种 CNY/USD/HKD */
    currency?: string;
    /** 已确认到款，只读 */
    confirmedClaimedAmount: number;
    /** 剩余可认领余额 = 结算 - 已确认认领，只读 */
    remainingBalance: number;
    /** 开票占用金额，只读 */
    invoicedOccupiedAmount?: number;
    /** 可开余额 = 结算 - 开票占用，只读 */
    invoiceOpenableAmount?: number;
    /** 备注（可选） */
    remark?: string;
    businessStaffUserId?: number;
    /** 创建时间，只读 */
    createTime: string;
  }

  /** 分页查询参数 */
  export interface PageQuery extends PageParam {
    orderNo?: string;
    entityCompanyDeptId?: number;
    importDate?: string;
    productName?: string;
    contactPerson?: string;
    /** 合同业务单号（正式关联） */
    contractApplicationNo?: string;
    /** 开票可选：可开余额 > 0 且有合同且非空 product_type_snapshot（后端 invoice-selectable） */
    onlyOpenable?: boolean;
    customerCompanyId?: number;
  }

  /** 创建/编辑表单数据（排除服务端只读字段；产品由服务端从合同派生，勿提交） */
  export type SaveForm = Pick<
    BusinessOrder,
    | 'entityCompanyDeptId'
    | 'orderDate'
    | 'contactPerson'
    | 'executionStartDate'
    | 'executionEndDate'
    | 'signedExecutionAmount'
  > & {
    id?: number;
    contractApplicationId?: number;
    payerName?: string;
    discountRate?: number;
    currency: string;
    remark?: string;
    businessStaffUserId?: number;
    /**
     * @deprecated EXP-70 兼容期：旧客户端可传，服务端忽略并从合同派生
     */
    productName?: string;
  };

  /** 导入结果 */
  export interface ImportResult {
    /** 成功写入的订单编号列表 */
    orderNos: string[];
    /** 跳过的重复行号列表 */
    skippedRows: number[];
    /** 失败行（行号 → 原因） */
    failureRows: Record<number, string>;
  }
}

/** 查询签单分页 */
export function getBusinessOrderPage(params: FinanceBusinessOrderApi.PageQuery) {
  return requestClient.get<PageResult<FinanceBusinessOrderApi.BusinessOrder>>(
    '/finance/business-order/page',
    { params },
  );
}

/** 获取签单详情 */
export function getBusinessOrder(id: number) {
  return requestClient.get<FinanceBusinessOrderApi.BusinessOrder>(
    '/finance/business-order/get',
    { params: { id } },
  );
}

/** 创建签单 */
export function createBusinessOrder(data: FinanceBusinessOrderApi.SaveForm) {
  return requestClient.post<number>('/finance/business-order/create', data);
}

/** 更新签单 */
export function updateBusinessOrder(data: FinanceBusinessOrderApi.SaveForm) {
  return requestClient.put<void>('/finance/business-order/update', data);
}

export interface FinanceBatchDeleteResult {
  deleted: number;
  errors?: string[];
}

/** 删除签单 */
export function deleteBusinessOrder(ids: number[]) {
  return requestClient.delete<FinanceBatchDeleteResult>(
    '/finance/business-order/delete',
    { params: { ids: ids.join(',') } },
  );
}

export function deleteBusinessOrderByQuery(
  data: FinanceBusinessOrderApi.PageQuery,
) {
  return requestClient.post<FinanceBatchDeleteResult>(
    '/finance/business-order/delete-query',
    data,
  );
}

export function exportBusinessOrderExcel(
  params: FinanceBusinessOrderApi.PageQuery,
) {
  return requestClient.download('/finance/business-order/export-excel', {
    params,
  });
}

/** 下载商务签单导入模板 */
export function importBusinessOrderTemplate() {
  return requestClient.download('/finance/business-order/get-import-template');
}

/** 导入签单 Excel（multipart：仅 file；主体公司在 Excel 列中） */
export function importBusinessOrder(file: File) {
  return requestClient.upload<FinanceBusinessOrderApi.ImportResult>(
    '/finance/business-order/import',
    { file },
  );
}

/** 将 failureRows 记录展开为表格数据 */
export function mapImportErrorRows(rows: Record<number, string>) {
  return Object.entries(rows).map(([rowNum, reason]) => ({
    rowNum: Number(rowNum),
    reason,
  }));
}
