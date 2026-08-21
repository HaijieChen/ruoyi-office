package cn.iocoder.yudao.module.hrm.service.payroll;

import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.EmployeeDO;
import cn.iocoder.yudao.module.hrm.dal.dataobject.payroll.PayrollBatchDO;
import cn.iocoder.yudao.module.hrm.dal.dataobject.payroll.PayrollLineDO;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.EmployeeMapper;
import cn.iocoder.yudao.module.hrm.dal.mysql.payroll.PayrollBatchMapper;
import cn.iocoder.yudao.module.hrm.dal.mysql.payroll.PayrollLineMapper;
import cn.iocoder.yudao.module.system.api.notify.NotifyMessageSendApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollBatchServiceTest {

    @InjectMocks
    private PayrollBatchServiceImpl service;

    @Mock
    private PayrollBatchMapper payrollBatchMapper;
    @Mock
    private PayrollLineMapper payrollLineMapper;
    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private MinWageService minWageService;
    @Mock
    private NotifyMessageSendApi notifyMessageSendApi;

    @Test
    void generateInsertsHrLineWithoutPublishing() {
        PayrollBatchDO batch = new PayrollBatchDO();
        batch.setId(1L);
        batch.setYearMonth(202608);
        batch.setStatus(PayrollBatchRules.DRAFT);
        when(payrollBatchMapper.selectOne(any())).thenReturn(batch);
        when(minWageService.effectiveOn(202608)).thenReturn(new BigDecimal("2690"));
        EmployeeDO emp = new EmployeeDO();
        emp.setId(9L);
        emp.setName("张三");
        emp.setRegularSalary(new BigDecimal("8000"));
        emp.setEntryDate(LocalDate.of(2024, 8, 1));
        emp.setUserId(88L);
        emp.setIdCard("310");
        emp.setBankAccount("6222");
        when(employeeMapper.selectList(any())).thenReturn(List.of(emp));

        service.generate(202608);

        ArgumentCaptor<PayrollLineDO> captor = ArgumentCaptor.forClass(PayrollLineDO.class);
        verify(payrollLineMapper).insert(captor.capture());
        assertEquals("张三", captor.getValue().getEmployeeName());
        assertEquals(false, captor.getValue().getSnapshot());
        assertEquals("310", captor.getValue().getIdCard());
    }

    @Test
    void publishCopiesSnapshotWithoutBankAndId() {
        PayrollBatchDO batch = new PayrollBatchDO();
        batch.setId(1L);
        batch.setYearMonth(202608);
        batch.setStatus(PayrollBatchRules.DRAFT);
        when(payrollBatchMapper.selectOne(any())).thenReturn(batch);
        PayrollLineDO hr = new PayrollLineDO();
        hr.setBatchId(1L);
        hr.setEmployeeName("张三");
        hr.setPayable(new BigDecimal("8200"));
        hr.setNet(new BigDecimal("7000"));
        hr.setUserId(88L);
        hr.setIdCard("310");
        hr.setBankAccount("6222");
        hr.setSnapshot(false);
        when(payrollLineMapper.selectList(any())).thenReturn(List.of(hr));

        service.publish(202608);

        ArgumentCaptor<PayrollLineDO> captor = ArgumentCaptor.forClass(PayrollLineDO.class);
        verify(payrollLineMapper).insert(captor.capture());
        assertEquals(true, captor.getValue().getSnapshot());
        assertNull(captor.getValue().getIdCard());
        assertNull(captor.getValue().getBankAccount());
        assertEquals(88L, captor.getValue().getUserId());
    }
}
