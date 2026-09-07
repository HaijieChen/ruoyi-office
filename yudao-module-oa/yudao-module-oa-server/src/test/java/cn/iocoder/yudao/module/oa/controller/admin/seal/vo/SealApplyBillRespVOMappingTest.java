package cn.iocoder.yudao.module.oa.controller.admin.seal.vo;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.oa.dal.dataobject.seal.SealApplyBillDO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SealApplyBillRespVOMappingTest {

    @Test
    void copiesDocumentTypeAndContractPartyFromDo() {
        SealApplyBillDO bill = new SealApplyBillDO();
        bill.setDocumentType("测试文件");
        bill.setContractParty("ABC公司");
        bill.setContractAmount(new BigDecimal("100.00"));
        bill.setUseType(1);
        SealApplyBillRespVO vo = BeanUtils.toBean(bill, SealApplyBillRespVO.class);
        assertEquals("测试文件", vo.getDocumentType());
        assertEquals("ABC公司", vo.getContractParty());
        assertEquals(new BigDecimal("100.00"), vo.getContractAmount());
        assertEquals(1, vo.getUseType());
    }
}
