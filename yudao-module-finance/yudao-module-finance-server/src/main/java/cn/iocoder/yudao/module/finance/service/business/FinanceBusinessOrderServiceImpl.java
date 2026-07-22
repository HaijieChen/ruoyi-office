package cn.iocoder.yudao.module.finance.service.business;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderSaveReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.business.FinanceBusinessOrderDO;
import cn.iocoder.yudao.module.finance.dal.mysql.business.FinanceBusinessOrderMapper;
import cn.iocoder.yudao.module.finance.enums.FinanceBusinessOrderStatusEnum;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.*;

@Service
@Validated
public class FinanceBusinessOrderServiceImpl implements FinanceBusinessOrderService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final FinanceBusinessOrderMapper businessOrderMapper;

    public FinanceBusinessOrderServiceImpl(FinanceBusinessOrderMapper businessOrderMapper) {
        this.businessOrderMapper = businessOrderMapper;
    }

    @Override
    public Long createBusinessOrder(FinanceBusinessOrderSaveReqVO createReqVO, Long ownerId) {
        validateBusinessOrderNoUnique(null, createReqVO.getOrderNo());
        validateBusinessOrderSave(createReqVO);
        FinanceBusinessOrderDO businessOrder = BeanUtils.toBean(createReqVO, FinanceBusinessOrderDO.class);
        businessOrder.setOwnerId(ownerId);
        businessOrderMapper.insert(businessOrder);
        return businessOrder.getId();
    }

    @Override
    public void updateBusinessOrder(FinanceBusinessOrderSaveReqVO updateReqVO) {
        FinanceBusinessOrderDO businessOrder = validateBusinessOrderExists(updateReqVO.getId());
        if (FinanceBusinessOrderStatusEnum.CLOSED.getStatus().equals(businessOrder.getStatus())) {
            throw exception(BUSINESS_ORDER_CLOSED);
        }
        validateBusinessOrderNoUnique(updateReqVO.getId(), updateReqVO.getOrderNo());
        validateBusinessOrderSave(updateReqVO);
        FinanceBusinessOrderDO updateObj = BeanUtils.toBean(updateReqVO, FinanceBusinessOrderDO.class);
        updateObj.setOwnerId(businessOrder.getOwnerId());
        businessOrderMapper.updateById(updateObj);
    }

    @Override
    public void deleteBusinessOrder(List<Long> ids) {
        for (Long id : ids) {
            FinanceBusinessOrderDO businessOrder = validateBusinessOrderExists(id);
            if (!FinanceBusinessOrderStatusEnum.DRAFT.getStatus().equals(businessOrder.getStatus())) {
                throw exception(BUSINESS_ORDER_DELETE_ONLY_DRAFT);
            }
        }
        businessOrderMapper.deleteByIds(ids);
    }

    @Override
    public FinanceBusinessOrderDO getBusinessOrder(Long id) {
        return businessOrderMapper.selectById(id);
    }

    @Override
    public PageResult<FinanceBusinessOrderDO> getBusinessOrderPage(FinanceBusinessOrderPageReqVO pageReqVO) {
        return businessOrderMapper.selectPage(pageReqVO);
    }

    private FinanceBusinessOrderDO validateBusinessOrderExists(Long id) {
        FinanceBusinessOrderDO businessOrder = businessOrderMapper.selectById(id);
        if (businessOrder == null) {
            throw exception(BUSINESS_ORDER_NOT_EXISTS);
        }
        return businessOrder;
    }

    private void validateBusinessOrderNoUnique(Long id, String orderNo) {
        FinanceBusinessOrderDO businessOrder = businessOrderMapper.selectByOrderNo(orderNo);
        if (businessOrder == null) {
            return;
        }
        if (id == null || !Objects.equals(businessOrder.getId(), id)) {
            throw exception(BUSINESS_ORDER_NO_EXISTS);
        }
    }

    private static void validateBusinessOrderSave(FinanceBusinessOrderSaveReqVO reqVO) {
        if (!reqVO.getCurrency().matches("^[A-Z]{3}$")) {
            throw exception(BUSINESS_ORDER_CURRENCY_INVALID);
        }
        if (!FinanceBusinessOrderStatusEnum.contains(reqVO.getStatus())) {
            throw exception(BUSINESS_ORDER_STATUS_INVALID);
        }
        BigDecimal receivableAmount = reqVO.getReceivableAmount();
        BigDecimal payableAmount = reqVO.getPayableAmount();
        if (receivableAmount.compareTo(ZERO) < 0 || payableAmount.compareTo(ZERO) < 0
                || receivableAmount.add(payableAmount).compareTo(ZERO) <= 0) {
            throw exception(BUSINESS_ORDER_AMOUNT_INVALID);
        }
    }

}
