import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace FinanceBankReceiptApi {
  /** 银行回单 */
  export interface BankReceipt {
    id: number;
    receiptNo: string;
    importDate: string;
    importerId: number;
    /** 主体公司组织部门编号 */
    entityCompanyDeptId?: number;
    /** 主体公司名称快照 */
    entityCompanyName?: string;
    bankAccount: string;
    transactionDate: string;
    payerName: string;
    payerAccount: string;
    transactionAmount: number;
    summary: string;
    bankSerialNo: string;
    /** 是否业务款（仅展示，不联动认领） */
    businessFund?: boolean;
    /** 款项类型备注（字典 finance_fund_type_remark） */
    fundTypeRemark?: string;
    /** 0=未认领 1=部分认领 2=完全认领 3=已关闭 */
    claimStatus: number;
    claimedAmount: number;
    unclaimedAmount: number;
  }

  /** 分页查询参数 */
  export interface ReceiptPageQuery extends PageParam {
    receiptNo?: string;
    bankAccount?: string;
    entityCompanyDeptId?: number;
    /** 交易日期范围 */
    transactionDate?: [string, string];
    payerName?: string;
    payerAccount?: string;
    bankSerialNo?: string;
    /** 导入日期范围 */
    importDate?: [string, string];
    /** 认领状态 */
    claimStatus?: number;
    /** 是否业务款 */
    businessFund?: boolean;
  }

  /** 新增/修改（transactionDate 传 epoch millis 或后端可解析时间） */
  export interface SaveForm {
    id?: number;
    entityCompanyDeptId: number;
    bankAccount: string;
    transactionDate: number | string;
    payerName: string;
    payerAccount?: string;
    transactionAmount: number;
    summary?: string;
    bankSerialNo: string;
    /** 是否业务款，必填，默认 true */
    businessFund: boolean;
    /** 款项类型备注，选填（字典 finance_fund_type_remark：利息收入/往来款项） */
    fundTypeRemark?: string;
  }

  /**
   * 导入结果
   * 后端 failureRows 类型为 Map<Integer,String>，序列化后为 Record<number, string>
   */
  export interface ReceiptImportResult {
    receiptNos: string[];
    failureRows: Record<number, string>;
  }

  export interface LifecycleReqVO {
    id: number;
    reason: string;
  }

  export interface LifecycleAuditVO {
    id: number;
    receiptId: number;
    action: number;
    operatorId: number;
    /** 操作人姓名快照 */
    operatorName?: string;
    actionTime: string;
    reason: string;
  }
}

/** 查询银行到款分页（含全部状态） */
export function getReceiptPage(params: FinanceBankReceiptApi.ReceiptPageQuery) {
  return requestClient.get<PageResult<FinanceBankReceiptApi.BankReceipt>>(
    '/finance/receipt/page',
    { params },
  );
}

/** 查询未认领回单分页 */
export function getUnclaimedReceiptPage(
  params: FinanceBankReceiptApi.ReceiptPageQuery,
) {
  return requestClient.get<PageResult<FinanceBankReceiptApi.BankReceipt>>(
    '/finance/receipt/unclaimed-page',
    { params },
  );
}

/** 获得银行到款详情 */
export function getReceipt(id: number) {
  return requestClient.get<FinanceBankReceiptApi.BankReceipt>(
    '/finance/receipt/get',
    { params: { id } },
  );
}

/** 创建银行到款 */
export function createReceipt(data: FinanceBankReceiptApi.SaveForm) {
  return requestClient.post<number>('/finance/receipt/create', data);
}

/** 更新银行到款 */
export function updateReceipt(data: FinanceBankReceiptApi.SaveForm) {
  return requestClient.put<boolean>('/finance/receipt/update', data);
}

/** 删除银行到款 */
export function deleteReceipt(ids: number[]) {
  return requestClient.delete<boolean>('/finance/receipt/delete', {
    params: { ids: ids.join(',') },
  });
}

/** 导入银行回单 XLSX */
export function importBankReceipt(file: File) {
  return requestClient.upload<FinanceBankReceiptApi.ReceiptImportResult>(
    '/finance/receipt/import',
    { file },
  );
}

/** 下载银行到款导入模板 */
export function importBankReceiptTemplate() {
  return requestClient.download('/finance/receipt/get-import-template');
}

export function closeReceipt(id: number, reason: string) {
  return requestClient.put<boolean>('/finance/receipt/close', { id, reason });
}

export function reopenReceipt(id: number, reason: string) {
  return requestClient.put<boolean>('/finance/receipt/reopen', { id, reason });
}

export function getLifecycleAuditList(receiptId: number) {
  return requestClient.get<FinanceBankReceiptApi.LifecycleAuditVO[]>(
    '/finance/receipt/lifecycle-audit-list',
    { params: { receiptId } },
  );
}

/**
 * 将后端 failureRows（Map<Integer,String> 序列化为 Record<number,string>）
 * 转换为按行号排序的展示行数组。纯函数，导出供测试验证。
 */
export function mapFailureRows(
  failureRows: FinanceBankReceiptApi.ReceiptImportResult['failureRows'],
): Array<{ rowNum: number; reason: string; label: string }> {
  return Object.entries(failureRows)
    .map(([key, reason]) => {
      const rowNum = Number(key);
      return { rowNum, reason, label: `第 ${rowNum} 行：${reason}` };
    })
    .sort((a, b) => a.rowNum - b.rowNum);
}
