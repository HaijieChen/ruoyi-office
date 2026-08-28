package cn.iocoder.yudao.module.hrm.controller.admin.employee.vo;

import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.EmployeeDO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmployeeWageCardRespVOTest {

    @Test
    void nullArchiveYieldsEmptyFields() {
        EmployeeWageCardRespVO vo = EmployeeWageCardRespVO.fromArchive(null);
        assertEquals("", vo.getName());
        assertEquals("", vo.getBankName());
        assertEquals("", vo.getBankAccount());
    }

    @Test
    void mapsNameAndWageCard() {
        EmployeeDO row = new EmployeeDO();
        row.setName("李四");
        row.setBankName("招商银行");
        row.setBankAccount("622588001111");
        EmployeeWageCardRespVO vo = EmployeeWageCardRespVO.fromArchive(row);
        assertEquals("李四", vo.getName());
        assertEquals("招商银行", vo.getBankName());
        assertEquals("622588001111", vo.getBankAccount());
    }
}
