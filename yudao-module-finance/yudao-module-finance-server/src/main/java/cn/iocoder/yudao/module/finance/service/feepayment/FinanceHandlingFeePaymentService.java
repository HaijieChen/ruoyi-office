package cn.iocoder.yudao.module.finance.service.feepayment;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.finance.controller.admin.feepayment.vo.FinanceHandlingFeePaymentImportExcelVO;
import cn.iocoder.yudao.module.finance.controller.admin.feepayment.vo.FinanceHandlingFeePaymentImportRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.feepayment.vo.FinanceHandlingFeePaymentPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.feepayment.vo.FinanceHandlingFeePaymentSaveReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.feepayment.FinanceHandlingFeePaymentDO;

import java.util.List;

public interface FinanceHandlingFeePaymentService {

    Long create(FinanceHandlingFeePaymentSaveReqVO reqVO);

    void update(FinanceHandlingFeePaymentSaveReqVO reqVO);

    void delete(Long id);

    FinanceHandlingFeePaymentDO get(Long id);

    PageResult<FinanceHandlingFeePaymentDO> getPage(FinanceHandlingFeePaymentPageReqVO reqVO);

    FinanceHandlingFeePaymentImportRespVO importExcel(List<FinanceHandlingFeePaymentImportExcelVO> rows);

}
