package cn.iocoder.yudao.module.finance.service.business;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderImportExcelVO;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderImportRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderSaveReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.business.FinanceBusinessOrderDO;
import jakarta.validation.Valid;

import java.util.List;

public interface FinanceBusinessOrderService {

    Long createBusinessOrder(@Valid FinanceBusinessOrderSaveReqVO createReqVO, Long importerId);

    void updateBusinessOrder(@Valid FinanceBusinessOrderSaveReqVO updateReqVO);

    void deleteBusinessOrder(List<Long> ids);

    FinanceBusinessOrderDO getBusinessOrder(Long id);

    PageResult<FinanceBusinessOrderDO> getBusinessOrderPage(FinanceBusinessOrderPageReqVO pageReqVO);

    PageResult<FinanceBusinessOrderDO> getClaimableBusinessOrderPage(FinanceBusinessOrderPageReqVO pageReqVO,
                                                                     Long importerId);

    FinanceBusinessOrderImportRespVO importBusinessOrderList(List<FinanceBusinessOrderImportExcelVO> importRows,
                                                               Long importerId);

}
