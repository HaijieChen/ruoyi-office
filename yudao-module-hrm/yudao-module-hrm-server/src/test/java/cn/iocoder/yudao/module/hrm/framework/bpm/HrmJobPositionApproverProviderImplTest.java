package cn.iocoder.yudao.module.hrm.framework.bpm;

import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.EmployeeDO;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.EmployeeEmploymentDO;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.EmployeeEmploymentMapper;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.EmployeeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HrmJobPositionApproverProviderImplTest {

    @InjectMocks
    private HrmJobPositionApproverProviderImpl provider;
    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private EmployeeEmploymentMapper employmentMapper;

    @Test
    void keepsUsersInStartCompanyByJobPosition() {
        EmployeeDO inCompany = new EmployeeDO();
        inCompany.setId(1L);
        inCompany.setUserId(11L);
        inCompany.setCompanyId(100L);
        inCompany.setJobPosition("manager");
        EmployeeDO extraOnly = new EmployeeDO();
        extraOnly.setId(2L);
        extraOnly.setUserId(22L);
        extraOnly.setCompanyId(200L);
        extraOnly.setJobPosition("manager");
        EmployeeDO other = new EmployeeDO();
        other.setId(3L);
        other.setUserId(33L);
        other.setCompanyId(200L);
        other.setJobPosition("manager");
        when(employeeMapper.selectListByJobPositions(any())).thenReturn(List.of(inCompany, extraOnly, other));
        EmployeeEmploymentDO extra = new EmployeeEmploymentDO();
        extra.setEmployeeId(2L);
        extra.setCompanyDeptId(100L);
        when(employmentMapper.selectListByCompanyDeptIds(any())).thenReturn(List.of(extra));

        Set<Long> ids = provider.listUserIdsByJobPositions(Set.of("manager"), 100L);
        assertEquals(Set.of(11L, 22L), ids);
    }
}
