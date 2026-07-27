package cn.iocoder.yudao.module.finance.service.receipt;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.finance.controller.admin.receipt.vo.FinanceReceiptImportExcelVO;
import cn.iocoder.yudao.module.finance.controller.admin.receipt.vo.FinanceReceiptImportRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.receipt.vo.FinanceReceiptPageReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.receipt.FinanceReceiptDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.receipt.FinanceReceiptLifecycleAuditDO;

import java.util.List;

public interface FinanceReceiptService {

    FinanceReceiptImportRespVO importReceiptList(List<FinanceReceiptImportExcelVO> importReceipts, Long importerId);

    PageResult<FinanceReceiptDO> getUnclaimedReceiptPage(FinanceReceiptPageReqVO pageReqVO);

    void closeReceipt(Long id, Long operatorId, String reason);

    void reopenReceipt(Long id, Long operatorId, String reason);

    List<FinanceReceiptLifecycleAuditDO> getLifecycleAuditList(Long receiptId);

}
