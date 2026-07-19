package cn.iocoder.yudao.module.erp.controller.admin.sale.vo.customer;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.erp.dal.dataobject.sale.ErpCustomerDO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ErpCustomerMailingAddressMappingTest {

    @Test
    void mapsMailingAddressAcrossSaveDataAndResponseModels() {
        ErpCustomerSaveReqVO request = new ErpCustomerSaveReqVO();
        request.setMailingAddress("上海市浦东新区张江路 1 号");

        ErpCustomerDO customer = BeanUtils.toBean(request, ErpCustomerDO.class);
        ErpCustomerRespVO response = BeanUtils.toBean(customer, ErpCustomerRespVO.class);

        assertEquals(request.getMailingAddress(), customer.getMailingAddress());
        assertEquals(customer.getMailingAddress(), response.getMailingAddress());
    }

}
