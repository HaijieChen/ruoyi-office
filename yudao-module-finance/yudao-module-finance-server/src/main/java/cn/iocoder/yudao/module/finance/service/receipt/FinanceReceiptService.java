package cn.iocoder.yudao.module.finance.service.receipt;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.finance.controller.admin.receipt.vo.FinanceReceiptImportExcelVO;
import cn.iocoder.yudao.module.finance.controller.admin.receipt.vo.FinanceReceiptImportRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.receipt.vo.FinanceReceiptPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.receipt.vo.FinanceReceiptSaveReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.receipt.FinanceReceiptDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.receipt.FinanceReceiptLifecycleAuditDO;
import jakarta.validation.Valid;

import java.util.List;

public interface FinanceReceiptService {

    Long createReceipt(@Valid FinanceReceiptSaveReqVO createReqVO, Long importerId);

    void updateReceipt(@Valid FinanceReceiptSaveReqVO updateReqVO);

    void deleteReceipt(List<Long> ids);

    FinanceReceiptDO getReceipt(Long id);

    PageResult<FinanceReceiptDO> getReceiptPage(FinanceReceiptPageReqVO pageReqVO);

    FinanceReceiptImportRespVO importReceiptList(List<FinanceReceiptImportExcelVO> importReceipts, Long importerId);

    PageResult<FinanceReceiptDO> getUnclaimedReceiptPage(FinanceReceiptPageReqVO pageReqVO);

    void closeReceipt(Long id, Long operatorId, String operatorName, String reason);

    void reopenReceipt(Long id, Long operatorId, String operatorName, String reason);

    List<FinanceReceiptLifecycleAuditDO> getLifecycleAuditList(Long receiptId);

}
