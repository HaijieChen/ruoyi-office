package cn.iocoder.yudao.module.hrm.service.employee;

import cn.iocoder.yudao.common.server.attachment.controller.vo.AttachmentSaveReqVO;
import cn.iocoder.yudao.common.server.attachment.dal.dataobject.AttachmentDO;
import cn.iocoder.yudao.common.server.attachment.service.AttachmentService;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.EmployeeContractVO;
import cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.EmployeeEducationVO;
import cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.EmployeeRespVO;
import cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.EmployeeSaveReqVO;
import cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.OnboardingAttachmentSaveReqVO;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.EmployeeContractDO;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.EmployeeDO;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.EmployeeEducationDO;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.OnboardingFileClaimDO;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.EmployeeContractMapper;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.EmployeeEducationMapper;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.EmployeeFamilyMapper;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.EmployeeMapper;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.EmployeeWorkExperienceMapper;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.OnboardingFileClaimMapper;
import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import cn.iocoder.yudao.module.infra.api.file.FileAccessApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileRespDTO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceImplTest {

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
    private FileAccessApi fileAccessApi;
    @Mock
    private OnboardingFileClaimMapper onboardingFileClaimMapper;
    @Mock
    private DeptApi deptApi;
    @Mock
    private AdminUserApi adminUserApi;
    @Mock
    private ConfigApi configApi;

    private EmployeeSaveReqVO baseReq() {
        EmployeeSaveReqVO req = new EmployeeSaveReqVO();
        req.setName("TEST_EMP_NAME");
        req.setSex(1);
        req.setMobile("10000000000"); // 明显无效手机
        req.setEmployeeStatus(1);
        return req;
    }

    private FileRespDTO privatePdf(long id) {
        FileRespDTO f = new FileRespDTO();
        f.setId(id);
        f.setName("TEST_scan.pdf");
        f.setPath("hrm-onboarding-private/TEST_scan.pdf");
        f.setUrl("https://files.example.test/hrm-onboarding-private/TEST_scan.pdf");
        f.setType("application/pdf");
        f.setSize(1024L);
        f.setConfigId(1L);
        return f;
    }

    private OnboardingFileClaimDO openClaim(String token, long fileId, long uploader) {
        return OnboardingFileClaimDO.builder()
                .id(1L)
                .claimToken(token)
                .fileId(fileId)
                .uploaderUserId(uploader)
                .purpose(OnboardingFileClaimDO.PURPOSE)
                .expireTime(LocalDateTime.now().plusHours(1))
                .build();
    }

    @BeforeEach
    void stubInsertId() {
        lenient().doAnswer(invocation -> {
            EmployeeDO archive = invocation.getArgument(0);
            archive.setId(100L);
            return 1;
        }).when(employeeArchiveMapper).insert(any(EmployeeDO.class));
        lenient().when(employeeArchiveMapper.selectMaxEmployeeNo()).thenReturn(0L);
    }

    @Test
    void createPersistsRosterFieldsContractsAndAttachmentsViaClaim() {
        EmployeeSaveReqVO req = baseReq();
        req.setSocialSecurityEnabled(true);
        req.setSocialSecurityStartMonth("2024-01");
        req.setHousingFundEnabled(true);
        req.setProbationSalary(new BigDecimal("8000.00"));
        req.setRegularSalary(new BigDecimal("10000.00"));
        req.setHouseholdType("1");
        req.setEmploymentForm("1");
        req.setEmergencyRelationship("配偶");
        req.setRecruitmentChannel("内推");
        req.setInterviewerName("TEST_INTERVIEWER");

        EmployeeContractVO c1 = new EmployeeContractVO();
        c1.setSequenceNo(1);
        c1.setContractType("1");
        c1.setStartDate(LocalDate.of(2020, 1, 1));
        c1.setEndDate(LocalDate.of(2023, 1, 1));
        EmployeeContractVO c2 = new EmployeeContractVO();
        c2.setSequenceNo(2);
        c2.setContractType("2");
        c2.setStartDate(LocalDate.of(2023, 1, 2));
        req.setContractList(List.of(c1, c2));

        EmployeeEducationVO edu = new EmployeeEducationVO();
        edu.setEducationLevel("6");
        edu.setHighestEducation(true);
        edu.setFirstEducation(true);
        edu.setSchoolName("TEST_UNIV");
        req.setEducationList(List.of(edu));

        OnboardingAttachmentSaveReqVO att = new OnboardingAttachmentSaveReqVO();
        att.setClaimToken("tok-owner");
        req.setOnboardingAttachments(List.of(att));

        when(onboardingFileClaimMapper.consumeIfOpen(eq("tok-owner"), eq(7L),
                eq(OnboardingFileClaimDO.PURPOSE), eq(100L), any(LocalDateTime.class)))
                .thenReturn(1);
        when(onboardingFileClaimMapper.selectByClaimToken("tok-owner"))
                .thenReturn(openClaim("tok-owner", 55L, 7L));
        when(fileAccessApi.getFile(55L)).thenReturn(privatePdf(55L));

        try (MockedStatic<SecurityFrameworkUtils> sec = mockStatic(SecurityFrameworkUtils.class)) {
            sec.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            Long id = employeeService.createEmployeeArchive(req);
            assertEquals(100L, id);
        }

        ArgumentCaptor<EmployeeDO> empCaptor = ArgumentCaptor.forClass(EmployeeDO.class);
        verify(employeeArchiveMapper).insert(empCaptor.capture());
        assertEquals(Boolean.TRUE, empCaptor.getValue().getSocialSecurityEnabled());
        assertEquals("2024-01", empCaptor.getValue().getSocialSecurityStartMonth());
        assertEquals("6", empCaptor.getValue().getEducation());

        verify(employeeContractMapper, times(2)).insert(any(EmployeeContractDO.class));
        verify(employeeEducationMapper).insert(any(EmployeeEducationDO.class));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AttachmentSaveReqVO>> attCaptor = ArgumentCaptor.forClass(List.class);
        verify(attachmentService).saveAttachmentListInternal(
                eq(EmployeeServiceImpl.ONBOARDING_ATTACHMENT_BUSINESS_TYPE),
                eq(100L),
                attCaptor.capture());
        assertEquals(55L, attCaptor.getValue().get(0).getFileId());
        assertEquals("TEST_scan.pdf", attCaptor.getValue().get(0).getFileName());
        assertEquals(1024L, attCaptor.getValue().get(0).getFileSize());
        assertEquals("", attCaptor.getValue().get(0).getFileUrl()); // 不落公开 URL
        verify(onboardingFileClaimMapper).consumeIfOpen(eq("tok-owner"), eq(7L),
                eq(OnboardingFileClaimDO.PURPOSE), eq(100L), any(LocalDateTime.class));
    }

    @Test
    void rejectsForgedOnboardingWithoutClaimOrId() {
        EmployeeSaveReqVO req = baseReq();
        req.setId(1L);
        OnboardingAttachmentSaveReqVO fake = new OnboardingAttachmentSaveReqVO();
        req.setOnboardingAttachments(List.of(fake));
        EmployeeDO existing = new EmployeeDO();
        existing.setId(1L);
        when(employeeArchiveMapper.selectById(1L)).thenReturn(existing);
        assertThrows(ServiceException.class, () -> employeeService.updateEmployeeArchive(req));
    }

    @Test
    void consumeClaimRejectsOtherUploaderSameTenant() {
        // 条件 UPDATE 影响行=0（上传者不匹配）
        when(onboardingFileClaimMapper.consumeIfOpen(eq("tok-x"), eq(200L),
                eq(OnboardingFileClaimDO.PURPOSE), eq(1L), any(LocalDateTime.class)))
                .thenReturn(0);
        assertThrows(ServiceException.class,
                () -> employeeService.consumeClaim("tok-x", 200L, 1L));
    }

    @Test
    void consumeClaimRejectsExpiredAndDoubleConsume() {
        // 过期/已消费：条件 UPDATE 均返回 0
        when(onboardingFileClaimMapper.consumeIfOpen(eq("tok-e"), eq(7L),
                eq(OnboardingFileClaimDO.PURPOSE), eq(1L), any(LocalDateTime.class)))
                .thenReturn(0);
        assertThrows(ServiceException.class,
                () -> employeeService.consumeClaim("tok-e", 7L, 1L));

        when(onboardingFileClaimMapper.consumeIfOpen(eq("tok-u"), eq(7L),
                eq(OnboardingFileClaimDO.PURPOSE), eq(1L), any(LocalDateTime.class)))
                .thenReturn(0);
        assertThrows(ServiceException.class,
                () -> employeeService.consumeClaim("tok-u", 7L, 1L));
    }

    @Test
    void consumeClaimRejectsWrongPurpose() {
        when(onboardingFileClaimMapper.consumeIfOpen(eq("tok-p"), eq(7L),
                eq(OnboardingFileClaimDO.PURPOSE), eq(1L), any(LocalDateTime.class)))
                .thenReturn(0);
        assertThrows(ServiceException.class,
                () -> employeeService.consumeClaim("tok-p", 7L, 1L));
    }

    @Test
    void rejectsOversizedAuthoritativeFileViaClaim() {
        EmployeeSaveReqVO req = baseReq();
        req.setId(1L);
        OnboardingAttachmentSaveReqVO att = new OnboardingAttachmentSaveReqVO();
        att.setClaimToken("tok-big");
        req.setOnboardingAttachments(List.of(att));
        FileRespDTO huge = privatePdf(9L);
        huge.setSize(21L * 1024 * 1024);
        when(onboardingFileClaimMapper.consumeIfOpen(eq("tok-big"), eq(7L),
                eq(OnboardingFileClaimDO.PURPOSE), eq(1L), any(LocalDateTime.class)))
                .thenReturn(1);
        when(onboardingFileClaimMapper.selectByClaimToken("tok-big"))
                .thenReturn(openClaim("tok-big", 9L, 7L));
        when(fileAccessApi.getFile(9L)).thenReturn(huge);
        EmployeeDO existing = new EmployeeDO();
        existing.setId(1L);
        when(employeeArchiveMapper.selectById(1L)).thenReturn(existing);
        try (MockedStatic<SecurityFrameworkUtils> sec = mockStatic(SecurityFrameworkUtils.class)) {
            sec.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertThrows(ServiceException.class, () -> employeeService.updateEmployeeArchive(req));
        }
    }

    @Test
    void rejectsExeExtensionFromAuthoritativeFileViaClaim() {
        EmployeeSaveReqVO req = baseReq();
        req.setId(1L);
        OnboardingAttachmentSaveReqVO att = new OnboardingAttachmentSaveReqVO();
        att.setClaimToken("tok-exe");
        req.setOnboardingAttachments(List.of(att));
        FileRespDTO exe = privatePdf(8L);
        exe.setName("malware.exe");
        when(onboardingFileClaimMapper.consumeIfOpen(eq("tok-exe"), eq(7L),
                eq(OnboardingFileClaimDO.PURPOSE), eq(1L), any(LocalDateTime.class)))
                .thenReturn(1);
        when(onboardingFileClaimMapper.selectByClaimToken("tok-exe"))
                .thenReturn(openClaim("tok-exe", 8L, 7L));
        when(fileAccessApi.getFile(8L)).thenReturn(exe);
        EmployeeDO existing = new EmployeeDO();
        existing.setId(1L);
        when(employeeArchiveMapper.selectById(1L)).thenReturn(existing);
        try (MockedStatic<SecurityFrameworkUtils> sec = mockStatic(SecurityFrameworkUtils.class)) {
            sec.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertThrows(ServiceException.class, () -> employeeService.updateEmployeeArchive(req));
        }
    }

    @Test
    void rejectsNonPrivateDirectoryFileViaClaim() {
        EmployeeSaveReqVO req = baseReq();
        req.setId(1L);
        OnboardingAttachmentSaveReqVO att = new OnboardingAttachmentSaveReqVO();
        att.setClaimToken("tok-pub");
        req.setOnboardingAttachments(List.of(att));
        FileRespDTO pub = privatePdf(3L);
        pub.setPath("/public/other/scan.pdf");
        when(onboardingFileClaimMapper.consumeIfOpen(eq("tok-pub"), eq(7L),
                eq(OnboardingFileClaimDO.PURPOSE), eq(1L), any(LocalDateTime.class)))
                .thenReturn(1);
        when(onboardingFileClaimMapper.selectByClaimToken("tok-pub"))
                .thenReturn(openClaim("tok-pub", 3L, 7L));
        when(fileAccessApi.getFile(3L)).thenReturn(pub);
        EmployeeDO existing = new EmployeeDO();
        existing.setId(1L);
        when(employeeArchiveMapper.selectById(1L)).thenReturn(existing);
        try (MockedStatic<SecurityFrameworkUtils> sec = mockStatic(SecurityFrameworkUtils.class)) {
            sec.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertThrows(ServiceException.class, () -> employeeService.updateEmployeeArchive(req));
        }
    }

    @Test
    void downloadLegacyNullFileIdByUniquePath() throws Exception {
        EmployeeDO emp = new EmployeeDO();
        emp.setId(1L);
        when(employeeArchiveMapper.selectById(1L)).thenReturn(emp);
        AttachmentDO legacy = new AttachmentDO();
        legacy.setId(2L);
        legacy.setBusinessType(EmployeeServiceImpl.ONBOARDING_ATTACHMENT_BUSINESS_TYPE);
        legacy.setBusinessId(1L);
        legacy.setFileId(null);
        legacy.setFilePath("legacy/path/TEST.pdf");
        legacy.setFileName("TEST.pdf");
        when(attachmentService.getAttachmentInternal(2L)).thenReturn(legacy);
        FileRespDTO unique = privatePdf(77L);
        unique.setPath("legacy/path/TEST.pdf");
        when(fileAccessApi.getUniqueFileByPath("legacy/path/TEST.pdf")).thenReturn(unique);
        when(fileAccessApi.getFileContent(77L)).thenReturn(new byte[]{1, 2, 3});
        when(fileAccessApi.getFile(77L)).thenReturn(unique);

        jakarta.servlet.http.HttpServletResponse response = mock(jakarta.servlet.http.HttpServletResponse.class);
        jakarta.servlet.ServletOutputStream out = mock(jakarta.servlet.ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(out);

        employeeService.downloadOnboardingAttachment(1L, 2L, response);
        verify(out).write(new byte[]{1, 2, 3});
        // 仅按权威 fileId 读内容（无裸 path fallback 方法）
        verify(fileAccessApi).getFileContent(77L);
    }

    @Test
    void downloadRejectsAmbiguousPathWithoutFileId() {
        EmployeeDO emp = new EmployeeDO();
        emp.setId(1L);
        when(employeeArchiveMapper.selectById(1L)).thenReturn(emp);
        AttachmentDO legacy = new AttachmentDO();
        legacy.setId(2L);
        legacy.setBusinessType(EmployeeServiceImpl.ONBOARDING_ATTACHMENT_BUSINESS_TYPE);
        legacy.setBusinessId(1L);
        legacy.setFileId(null);
        legacy.setFilePath("dup/path.pdf");
        when(attachmentService.getAttachmentInternal(2L)).thenReturn(legacy);
        when(fileAccessApi.getUniqueFileByPath("dup/path.pdf")).thenReturn(null);

        assertThrows(ServiceException.class, () ->
                employeeService.downloadOnboardingAttachment(1L, 2L,
                        mock(jakarta.servlet.http.HttpServletResponse.class)));
    }

    @Test
    void updateRejectsMoreThanFourContracts() {
        EmployeeSaveReqVO req = baseReq();
        req.setId(1L);
        List<EmployeeContractVO> contracts = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            EmployeeContractVO c = new EmployeeContractVO();
            c.setSequenceNo(i);
            c.setStartDate(LocalDate.of(2020, 1, 1));
            contracts.add(c);
        }
        req.setContractList(contracts);
        assertThrows(ServiceException.class, () -> employeeService.updateEmployeeArchive(req));
    }

    @Test
    void updateRejectsDuplicateEducationRoles() {
        EmployeeSaveReqVO req = baseReq();
        req.setId(1L);
        EmployeeEducationVO e1 = new EmployeeEducationVO();
        e1.setHighestEducation(true);
        EmployeeEducationVO e2 = new EmployeeEducationVO();
        e2.setHighestEducation(true);
        req.setEducationList(List.of(e1, e2));
        assertThrows(ServiceException.class, () -> employeeService.updateEmployeeArchive(req));
    }

    @Test
    void updateRejectsContractEndBeforeStart() {
        EmployeeSaveReqVO req = baseReq();
        req.setId(1L);
        EmployeeContractVO c = new EmployeeContractVO();
        c.setSequenceNo(1);
        c.setStartDate(LocalDate.of(2024, 1, 1));
        c.setEndDate(LocalDate.of(2023, 1, 1));
        req.setContractList(List.of(c));
        assertThrows(ServiceException.class, () -> employeeService.updateEmployeeArchive(req));
    }

    @Test
    void updateRejectsContractMissingStartDate() {
        EmployeeSaveReqVO req = baseReq();
        req.setId(1L);
        EmployeeContractVO c = new EmployeeContractVO();
        c.setSequenceNo(1);
        c.setStartDate(null);
        req.setContractList(List.of(c));
        assertThrows(ServiceException.class, () -> employeeService.updateEmployeeArchive(req));
    }

    @Test
    void getBuildsAgeTenureAndCurrentContract() {
        EmployeeDO employee = new EmployeeDO();
        employee.setId(42L);
        employee.setName("TEST_EMP");
        employee.setBirthday(LocalDate.of(1990, 8, 12));
        employee.setEntryDate(LocalDate.of(2020, 6, 15));
        employee.setMaritalStatus("已婚");
        employee.setFertilityStatus("已育");
        when(employeeArchiveMapper.selectById(42L)).thenReturn(employee);
        when(employeeWorkExperienceMapper.selectListByEmployeeId(42L)).thenReturn(List.of());
        when(employeeEducationMapper.selectListByEmployeeId(42L)).thenReturn(List.of());
        when(employeeFamilyMapper.selectListByEmployeeId(42L)).thenReturn(List.of());
        when(attachmentService.getAttachmentListByBusinessInternal(anyString(), eq(42L))).thenReturn(List.of());

        EmployeeContractDO c1 = EmployeeContractDO.builder()
                .id(1L).employeeId(42L).sequenceNo(1)
                .contractType("1").startDate(LocalDate.of(2020, 1, 1)).endDate(LocalDate.of(2023, 1, 1))
                .build();
        EmployeeContractDO c2 = EmployeeContractDO.builder()
                .id(2L).employeeId(42L).sequenceNo(2)
                .contractType("2").startDate(LocalDate.of(2023, 1, 2)).endDate(null)
                .build();
        when(employeeContractMapper.selectListByEmployeeId(42L)).thenReturn(List.of(c1, c2));

        EmployeeRespVO resp = employeeService.getEmployeeArchive(42L);
        assertNotNull(resp);

        employeeService.fillDerivedFields(resp, LocalDate.of(2026, 8, 11));
        assertEquals(35, resp.getAge());
        assertEquals(73, resp.getCompanyTenureMonths());
        assertEquals(2, resp.getContractSignCount());
        assertEquals("2", resp.getCurrentContractType());
        assertEquals("已婚/已育", resp.getMarriageChildbearingSummary());
    }

    @Test
    void deleteRemovesContractsAndAttachmentMetadata() {
        EmployeeDO employee = new EmployeeDO();
        employee.setId(9L);
        employee.setUserGenerated(false);
        when(employeeArchiveMapper.selectById(9L)).thenReturn(employee);

        employeeService.deleteEmployeeArchive(9L);

        verify(employeeContractMapper).deleteByEmployeeId(9L);
        verify(attachmentService).deleteAttachmentByBusiness(
                EmployeeServiceImpl.ONBOARDING_ATTACHMENT_BUSINESS_TYPE, 9L);
        verify(employeeArchiveMapper).deleteById(9L);
    }

    @Test
    void updateOmittingContractListPreservesContracts() {
        EmployeeSaveReqVO req = baseReq();
        req.setId(1L);
        req.setContractList(null);
        req.setOnboardingAttachments(null);
        EmployeeDO existing = new EmployeeDO();
        existing.setId(1L);
        when(employeeArchiveMapper.selectById(1L)).thenReturn(existing);

        employeeService.updateEmployeeArchive(req);

        verify(employeeContractMapper, never()).deleteByEmployeeId(anyLong());
        verify(employeeContractMapper, never()).insert(any(EmployeeContractDO.class));
        verify(attachmentService, never()).saveAttachmentList(anyString(), anyLong(), any());
    }

    @Test
    void updateEmptyContractListClearsContracts() {
        EmployeeSaveReqVO req = baseReq();
        req.setId(1L);
        req.setContractList(List.of());
        req.setOnboardingAttachments(List.of());
        EmployeeDO existing = new EmployeeDO();
        existing.setId(1L);
        when(employeeArchiveMapper.selectById(1L)).thenReturn(existing);

        employeeService.updateEmployeeArchive(req);

        verify(employeeContractMapper).deleteByEmployeeId(1L);
        verify(employeeContractMapper, never()).insert(any(EmployeeContractDO.class));
        verify(attachmentService).saveAttachmentListInternal(
                eq(EmployeeServiceImpl.ONBOARDING_ATTACHMENT_BUSINESS_TYPE), eq(1L), eq(List.of()));
    }

    @Test
    void sparseUpdateOmitsKeepExplicitNullClears() {
        EmployeeDO old = new EmployeeDO();
        old.setId(1L);
        old.setSocialSecurityEnabled(true);
        old.setSocialSecurityStartMonth("2024-01");
        old.setProbationSalary(new BigDecimal("8000"));
        old.setRecruitmentChannel("内推");
        when(employeeArchiveMapper.selectById(1L)).thenReturn(old);

        EmployeeSaveReqVO req = baseReq();
        req.setId(1L);
        req.setRecruitmentChannel(null);
        req.setProbationSalary(new BigDecimal("9000"));

        employeeService.updateEmployeeArchive(req);

        ArgumentCaptor<EmployeeDO> captor = ArgumentCaptor.forClass(EmployeeDO.class);
        verify(employeeArchiveMapper).updateById(captor.capture());
        EmployeeDO saved = captor.getValue();
        assertEquals(Boolean.TRUE, saved.getSocialSecurityEnabled());
        assertEquals("2024-01", saved.getSocialSecurityStartMonth());
        assertNull(saved.getRecruitmentChannel());
        assertEquals(new BigDecimal("9000"), saved.getProbationSalary());
    }

    /**
     * #7 反例1：旧值已参保有月份，仅提交 enabled=true（省略 month）→ 保留旧月份，不误拒
     */
    @Test
    void socialSecurityOnlyEnabledTrueKeepsOldMonth() {
        EmployeeDO old = new EmployeeDO();
        old.setId(1L);
        old.setSocialSecurityEnabled(true);
        old.setSocialSecurityStartMonth("2024-01");
        when(employeeArchiveMapper.selectById(1L)).thenReturn(old);

        EmployeeSaveReqVO req = baseReq();
        req.setId(1L);
        req.setSocialSecurityEnabled(true); // month 省略

        employeeService.updateEmployeeArchive(req);

        ArgumentCaptor<EmployeeDO> captor = ArgumentCaptor.forClass(EmployeeDO.class);
        verify(employeeArchiveMapper).updateById(captor.capture());
        assertEquals(Boolean.TRUE, captor.getValue().getSocialSecurityEnabled());
        assertEquals("2024-01", captor.getValue().getSocialSecurityStartMonth());
    }

    /**
     * #7 反例2：省略 enabled（old=true）、显式清空月份 → 有效已参保无月份，拒绝
     */
    @Test
    void socialSecurityOmitEnabledClearMonthRejectedWhenOldEnabled() {
        EmployeeDO old = new EmployeeDO();
        old.setId(1L);
        old.setSocialSecurityEnabled(true);
        old.setSocialSecurityStartMonth("2024-01");
        when(employeeArchiveMapper.selectById(1L)).thenReturn(old);

        EmployeeSaveReqVO req = baseReq();
        req.setId(1L);
        req.setSocialSecurityStartMonth(null); // 显式清空

        assertThrows(ServiceException.class, () -> employeeService.updateEmployeeArchive(req));
        verify(employeeArchiveMapper, never()).updateById(any(EmployeeDO.class));
    }

    /**
     * #7 反例3：旧值未参保，仅提交月份 → 有效未参保强制 month=NULL 落库
     */
    @Test
    void socialSecurityOnlyMonthWhenOldDisabledForcesNullMonth() {
        EmployeeDO old = new EmployeeDO();
        old.setId(1L);
        old.setSocialSecurityEnabled(false);
        old.setSocialSecurityStartMonth(null);
        when(employeeArchiveMapper.selectById(1L)).thenReturn(old);

        EmployeeSaveReqVO req = baseReq();
        req.setId(1L);
        req.setSocialSecurityStartMonth("2024-06");

        employeeService.updateEmployeeArchive(req);

        ArgumentCaptor<EmployeeDO> captor = ArgumentCaptor.forClass(EmployeeDO.class);
        verify(employeeArchiveMapper).updateById(captor.capture());
        assertEquals(Boolean.FALSE, captor.getValue().getSocialSecurityEnabled());
        assertNull(captor.getValue().getSocialSecurityStartMonth());
    }

    @Test
    void updateSocialSecurityFalseWritesNullStartMonth() {
        EmployeeSaveReqVO req = baseReq();
        req.setId(1L);
        req.setSocialSecurityEnabled(false);
        req.setSocialSecurityStartMonth("2024-01");
        EmployeeDO existing = new EmployeeDO();
        existing.setId(1L);
        existing.setSocialSecurityStartMonth("2023-06");
        when(employeeArchiveMapper.selectById(1L)).thenReturn(existing);

        employeeService.updateEmployeeArchive(req);

        ArgumentCaptor<EmployeeDO> captor = ArgumentCaptor.forClass(EmployeeDO.class);
        verify(employeeArchiveMapper).updateById(captor.capture());
        assertNull(captor.getValue().getSocialSecurityStartMonth());
        assertEquals(Boolean.FALSE, captor.getValue().getSocialSecurityEnabled());
    }

    @Test
    void rosterNullableFieldsUseAlwaysUpdateStrategy() throws Exception {
        var field = EmployeeDO.class.getDeclaredField("socialSecurityStartMonth");
        var annotation = field.getAnnotation(com.baomidou.mybatisplus.annotation.TableField.class);
        assertNotNull(annotation);
        assertEquals(com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS, annotation.updateStrategy());
    }

    @Test
    void downloadRejectsAttachmentNotBelongingToEmployee() {
        EmployeeDO emp = new EmployeeDO();
        emp.setId(1L);
        when(employeeArchiveMapper.selectById(1L)).thenReturn(emp);
        AttachmentDO foreign = new AttachmentDO();
        foreign.setId(2L);
        foreign.setBusinessType(EmployeeServiceImpl.ONBOARDING_ATTACHMENT_BUSINESS_TYPE);
        foreign.setBusinessId(999L);
        when(attachmentService.getAttachmentInternal(2L)).thenReturn(foreign);

        assertThrows(ServiceException.class, () ->
                employeeService.downloadOnboardingAttachment(1L, 2L, mock(jakarta.servlet.http.HttpServletResponse.class)));
    }

    @Test
    void fileApiNoLongerExposesGetFileContentOrReadPresignRpc() throws Exception {
        // 静态契约：FileApi 不得再声明 getFile / getFileContent / presignGetUrl（/rpc-api permitAll）
        assertTrue(java.util.Arrays.stream(
                        cn.iocoder.yudao.module.infra.api.file.FileApi.class.getDeclaredMethods())
                .noneMatch(m -> m.getName().equals("getFile")
                        || m.getName().equals("getFileContent")
                        || m.getName().equals("presignGetUrl")));
        assertNotNull(FileAccessApi.class.getMethod("getFileContent", Long.class));
        assertNotNull(FileAccessApi.class.getMethod("getUniqueFileByPath", String.class));
        assertFalse(FileAccessApi.class.isAnnotationPresent(org.springframework.web.bind.annotation.RestController.class));
    }

}
