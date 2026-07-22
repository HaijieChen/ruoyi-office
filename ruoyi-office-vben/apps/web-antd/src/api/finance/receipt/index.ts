import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace FinanceBankReceiptApi {
  /** 银行回单 */
  export interface BankReceipt {
    id: number;
    receiptNo: string;
    importDate: string;
    importerId: number;
    bankAccount: string;
    transactionDate: string;
    payerName: string;
    payerAccount: string;
    transactionAmount: number;
    summary: string;
    bankSerialNo: string;
    /** 0=未认领 1=部分认领 2=完全认领 3=已关闭 */
    claimStatus: number;
    claimedAmount: number;
    unclaimedAmount: number;
  }

  /** 未认领回单分页查询参数 */
  export interface UnclaimedReceiptPageQuery extends PageParam {
    receiptNo?: string;
    bankAccount?: string;
    /** 交易日期范围，传给后端的格式为 [startDate, endDate] */
    transactionDate?: [string, string];
    payerName?: string;
    payerAccount?: string;
    bankSerialNo?: string;
    /** 导入日期范围，传给后端的格式为 [startDate, endDate] */
    importDate?: [string, string];
  }

  /**
   * 导入结果
   * 后端 failureRows 类型为 Map<Integer, String>，序列化后为 Record<number, string>
   */
  export interface ReceiptImportResult {
    receiptNos: string[];
    failureRows: Record<number, string>;
  }
}

/** 查询未认领回单分页 */
export function getUnclaimedReceiptPage(
  params: FinanceBankReceiptApi.UnclaimedReceiptPageQuery,
) {
  return requestClient.get<
    PageResult<FinanceBankReceiptApi.BankReceipt>
  >('/finance/receipt/unclaimed-page', { params });
}

/** 导入银行回单 XLSX */
export function importBankReceipt(file: File) {
  return requestClient.upload<FinanceBankReceiptApi.ReceiptImportResult>(
    '/finance/receipt/import',
    { file },
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
