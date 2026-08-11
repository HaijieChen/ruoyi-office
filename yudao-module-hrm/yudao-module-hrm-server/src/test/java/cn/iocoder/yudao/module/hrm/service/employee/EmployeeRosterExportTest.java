package cn.iocoder.yudao.module.hrm.service.employee;

import cn.idev.excel.annotation.ExcelProperty;
import cn.iocoder.yudao.common.server.attachment.dal.dataobject.AttachmentDO;
import cn.iocoder.yudao.common.server.attachment.service.AttachmentService;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.EmployeePageReqVO;
import cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.EmployeeRosterExportVO;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.EmployeeContractDO;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.EmployeeDO;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.EmployeeEducationDO;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.EmployeeContractMapper;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.EmployeeEducationMapper;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.EmployeeFamilyMapper;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.EmployeeMapper;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.EmployeeWorkExperienceMapper;
import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeRosterExportTest {

    @InjectMocks
    private EmployeeServiceImpl employeeService;

    @Mock
    private EmployeeMapper employeeArchiveMapper;
    @Mock
    private EmployeeWorkExperienceMapper employeeWorkExperienceMapper;
    @Mock
    private EmployeeEducationMapper employeeEducationMapper;
    @Mock
    private EmployeeFamilyMapper employeeFamilyMapper;
    @Mock
    private EmployeeContractMapper employeeContractMapper;
    @Mock
    private AttachmentService attachmentService;
    @Mock
    private DeptApi deptApi;
    @Mock
    private AdminUserApi adminUserApi;
    @Mock
    private ConfigApi configApi;

    @Test
    void exportHeaderOrderMatchesTemplate() {
        assertEquals(List.of("序号", "是否缴纳社保", "是否缴纳公积金", "单位名称"),
                firstHeaders(EmployeeRosterExportVO.class, 4));
        assertEquals(52, firstHeaders(EmployeeRosterExportVO.class, 100).size());
        List<String> headers = firstHeaders(EmployeeRosterExportVO.class, 52);
        assertEquals("入职资料", headers.get(51));
        assertEquals("一次合同起止日期", headers.get(45));
    }

    @Test
    void exportFlattensEducationContractsAndAttachmentCount() {
        EmployeeDO e1 = new EmployeeDO();
        e1.setId(1L);
        e1.setName("甲");
        e1.setSocialSecurityEnabled(true);
        e1.setSocialSecurityStartMonth("2024-01");
        e1.setBirthday(LocalDate.of(1990, 1, 1));
        e1.setEntryDate(LocalDate.of(2020, 1, 1));
        e1.setEmployeeStatus(1);

        EmployeeDO e2 = new EmployeeDO();
        e2.setId(2L);
        e2.setName("乙");
        // 未知不默认否
        e2.setSocialSecurityEnabled(null);

        when(employeeArchiveMapper.selectPage(any())).thenReturn(new PageResult<>(List.of(e1, e2), 2L));

        EmployeeEducationDO highest = EmployeeEducationDO.builder()
                .employeeId(1L).educationLevel("本科").educationType("1").degree("学士")
                .highestEducation(true).firstEducation(false)
                .schoolName("A大").major("CS").endTime(LocalDate.of(2012, 6, 1)).build();
        EmployeeEducationDO first = EmployeeEducationDO.builder()
                .employeeId(1L).educationLevel("大专").firstEducation(true).highestEducation(false)
                .schoolName("B专").major("软工").endTime(LocalDate.of(2009, 6, 1)).build();
        when(employeeEducationMapper.selectListByEmployeeIds(anyCollection()))
                .thenReturn(List.of(highest, first));

        EmployeeContractDO c1 = EmployeeContractDO.builder()
                .employeeId(1L).sequenceNo(1).contractType("1")
                .startDate(LocalDate.of(2020, 1, 1)).endDate(LocalDate.of(2023, 1, 1)).build();
        EmployeeContractDO c2 = EmployeeContractDO.builder()
                .employeeId(1L).sequenceNo(2).contractType("2")
                .startDate(LocalDate.of(2023, 1, 2)).endDate(null).build();
        when(employeeContractMapper.selectListByEmployeeIds(anyCollection()))
                .thenReturn(List.of(c1, c2));

        AttachmentDO a1 = new AttachmentDO();
        a1.setBusinessId(1L);
        a1.setFilePath("/a");
        AttachmentDO a2 = new AttachmentDO();
        a2.setBusinessId(1L);
        a2.setFilePath("/b");
        when(attachmentService.getAttachmentListByBusinessIds(anyString(), anyCollection()))
                .thenReturn(List.of(a1, a2));

        List<EmployeeRosterExportVO> list = employeeService.getEmployeeRosterExportList(new EmployeePageReqVO());
        assertEquals(2, list.size());

        EmployeeRosterExportVO row1 = list.get(0);
        assertEquals(1, row1.getSequenceNo());
        assertEquals("是", row1.getSocialSecurityEnabled());
        assertEquals("本科", row1.getHighestEducation());
        assertEquals("大专", row1.getFirstEducation());
        assertEquals("2020-01-01至2023-01-01", row1.getContract1Range());
        assertEquals("2023-01-02", row1.getContract2Range());
        assertEquals(2, row1.getContractSignCount());
        assertEquals("2", row1.getCurrentContractType());
        assertEquals("已上传2份", row1.getOnboardingAttachmentStatus());

        EmployeeRosterExportVO row2 = list.get(1);
        assertNull(row2.getSocialSecurityEnabled());
        assertEquals("未上传", row2.getOnboardingAttachmentStatus());

        // 批量查询各一次，禁止 N+1
        verify(employeeEducationMapper, times(1)).selectListByEmployeeIds(anyCollection());
        verify(employeeContractMapper, times(1)).selectListByEmployeeIds(anyCollection());
        verify(attachmentService, times(1)).getAttachmentListByBusinessIds(anyString(), anyCollection());
    }

    private static List<String> firstHeaders(Class<?> type, int limit) {
        return Arrays.stream(type.getDeclaredFields())
                .map(field -> field.getAnnotation(ExcelProperty.class))
                .filter(Objects::nonNull)
                .map(annotation -> annotation.value()[0])
                .limit(limit)
                .toList();
    }

}
