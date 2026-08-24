package cn.iocoder.yudao.module.finance.service.invoice;

import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationImportExcelVO;
import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationImportRespVO;

import java.util.List;

public interface FinanceInvoiceApplicationImportService {

    /**
     * 历史开票导入：直接 APPROVED + 办票完成，不启流程、不占商务单。
     * 冲突：文件内或库内开票申请单号重复 → 行级失败。
     */
    FinanceInvoiceApplicationImportRespVO importHistorical(
            List<FinanceInvoiceApplicationImportExcelVO> rows);
}
