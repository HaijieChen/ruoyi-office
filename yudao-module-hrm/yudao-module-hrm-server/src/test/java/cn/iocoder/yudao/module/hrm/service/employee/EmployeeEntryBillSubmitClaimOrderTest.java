package cn.iocoder.yudao.module.hrm.service.employee;

import cn.iocoder.yudao.common.server.attachment.service.AttachmentService;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.EmployeeEntryBillSaveReqVO;
import cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.OnboardingAttachmentSaveReqVO;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.EmployeeEntryBillDO;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.EmployeeEntryBillEducationMapper;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.EmployeeEntryBillFamilyMapper;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.EmployeeEntryBillMapper;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.EmployeeEntryBillWorkExperienceMapper;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.EmployeeMapper;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.OnboardingFileClaimMapper;
import cn.iocoder.yudao.module.infra.api.file.FileAccessApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * H3 #5：无效 claim 必须在 BPM 远端提交前失败（processInstanceApi 0 次）。
 */
@ExtendWith(MockitoExtension.class)
class EmployeeEntryBillSubmitClaimOrderTest {

    @InjectMocks
    private EmployeeEntryBillServiceImpl service;

    @Mock private EmployeeEntryBillMapper employeeEntryBillMapper;
    @Mock private AttachmentService attachmentService;
    @Mock private FileAccessApi fileAccessApi;
    @Mock private OnboardingFileClaimMapper onboardingFileClaimMapper;
    @Mock private BpmProcessInstanceApi processInstanceApi;
    @Mock private EmployeeService employeeService;
    @Mock private EmployeeMapper employeeMapper;
    @Mock private EmployeeEntryBillWorkExperienceMapper entryBillWorkExperienceMapper;
    @Mock private EmployeeEntryBillEducationMapper entryBillEducationMapper;
    @Mock private EmployeeEntryBillFamilyMapper entryBillFamilyMapper;

    @Test
    void submit_invalidClaim_neverCallsBpm() {
        EmployeeEntryBillSaveReqVO req = new EmployeeEntryBillSaveReqVO();
        req.setBillCode("RZ202608120001"); // 避免 BillCodeUtils 依赖 Spring 上下文
        req.setName("张三");
        req.setSex(1);
        req.setMobile("13800000000");
        req.setIdCard("110101199001011234");
        req.setEntryDate(LocalDate.of(2026, 1, 1));
        req.setEmpDeptId(1L);
        req.setEmpDeptName("研发");
        req.setEmpCompanyId(1L);
        req.setEmpCompanyName("宇擎");
        req.setEmployeeStatus(2);
        req.setCompanyId(1L);
        req.setCompanyName("宇擎");
        req.setCreator("7");
        OnboardingAttachmentSaveReqVO att = new OnboardingAttachmentSaveReqVO();
        att.setClaimToken("bad-token");
        req.setAttachments(List.of(att));

        when(employeeMapper.selectCount(any())).thenReturn(0L);
        doAnswer(inv -> {
            EmployeeEntryBillDO bill = inv.getArgument(0);
            bill.setId(88L);
            return true;
        }).when(employeeEntryBillMapper).insertOrUpdate(any(EmployeeEntryBillDO.class));

        when(onboardingFileClaimMapper.consumeIfOpen(eq("bad-token"), eq(7L),
                anyString(), eq(88L), any())).thenReturn(0);

        try (MockedStatic<SecurityFrameworkUtils> sec = mockStatic(SecurityFrameworkUtils.class)) {
            sec.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertThrows(ServiceException.class, () -> service.submitEmployeeEntryBill(req));
        }

        verify(processInstanceApi, never()).submitProcessInstance(anyLong(), any());
        verify(attachmentService, never()).saveAttachmentListInternal(anyString(), anyLong(), anyList());
    }

}
