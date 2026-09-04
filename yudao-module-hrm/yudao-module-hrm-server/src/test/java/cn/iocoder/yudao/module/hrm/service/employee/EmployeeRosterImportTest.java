package cn.iocoder.yudao.module.hrm.service.employee;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.hutool.extra.spring.SpringUtil;
import cn.idev.excel.annotation.ExcelProperty;
import cn.iocoder.yudao.common.server.attachment.service.AttachmentService;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.EmployeeRosterImportExcelVO;
import cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.EmployeeRosterImportRespVO;
import cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.EmployeeSaveReqVO;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.EmployeeDO;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.EmployeeContractMapper;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.EmployeeEducationMapper;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.EmployeeFamilyMapper;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.EmployeeMapper;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.EmployeeWorkExperienceMapper;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.OnboardingFileClaimMapper;
import cn.iocoder.yudao.module.hrm.enums.EmployeeStatusEnum;
import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import cn.iocoder.yudao.module.infra.api.file.FileAccessApi;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 文枢花名册导入：表头与官方附件对齐 + 映射/upsert 行为。
 */
@ExtendWith(MockitoExtension.class)
class EmployeeRosterImportTest {

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
    @Mock
    private cn.iocoder.yudao.module.hrm.dal.mysql.employee.EmployeeEmploymentMapper employeeEmploymentMapper;

    private static final String DEPT_NAME = "总经办";
    private static final Long DEPT_ID = 1001L;
    private static final Long COMPANY_ID = 100L;

    @BeforeEach
    void stubDefaultDeptApi() {
        DeptRespDTO dept = new DeptRespDTO();
        dept.setId(DEPT_ID);
        dept.setName(DEPT_NAME);
        dept.setParentId(COMPANY_ID);
        dept.setStatus(0);
        dept.setOrgType("0");
        lenient().when(deptApi.getSimpleDeptList()).thenReturn(CommonResult.success(List.of(dept)));

        DeptRespDTO company = new DeptRespDTO();
        company.setId(COMPANY_ID);
        company.setName("文枢科技");
        company.setOrgType("1");
        company.setStatus(0);
        company.setParentId(0L);
        lenient().when(deptApi.getDept(DEPT_ID)).thenReturn(CommonResult.success(dept));
        lenient().when(deptApi.getDept(COMPANY_ID)).thenReturn(CommonResult.success(company));
    }

    @Test
    void importExcelVoHeadersMatchTemplateAttachmentExactly() throws Exception {
        assertEquals(52, EmployeeRosterImportExcelVO.TEMPLATE_HEADERS.length);

        // 注解 value 与 TEMPLATE_HEADERS 逐字一致（含换行与长 title）
        Field[] fields = EmployeeRosterImportExcelVO.class.getDeclaredFields();
        List<String> annotated = new ArrayList<>();
        for (Field f : fields) {
            ExcelProperty prop = f.getAnnotation(ExcelProperty.class);
            if (prop == null) {
                continue;
            }
            assertEquals(1, prop.value().length);
            annotated.add(prop.value()[0]);
        }
        assertEquals(55, annotated.size());
        for (int i = 0; i < 52; i++) {
            assertEquals(EmployeeRosterImportExcelVO.TEMPLATE_HEADERS[i], annotated.get(i),
                    "column index " + i);
        }
        assertEquals("任职单位", annotated.get(52));
        assertEquals("任职部门", annotated.get(53));
        assertEquals("员工工号", annotated.get(54));

        // 关键列
        assertEquals("最高学历\n毕业学校", annotated.get(27));
        assertEquals("第一学历\n毕业学校", annotated.get(30));
        assertTrue(annotated.get(51).startsWith("入职资料（系统里可以标注"));
        assertEquals(
                "入职资料（系统里可以标注一个上传附件的地方，我们可以扫描上传入职资料）",
                annotated.get(51));
    }

    @Test
    void bundledImportTemplateKeepsOfficialHeadersAndAppendsEmployments() throws Exception {
        ClassPathResource resource = new ClassPathResource("excel/文枢花名册导入模板.xlsx");
        assertTrue(resource.exists(), "classpath template missing");
        try (InputStream in = resource.getInputStream();
             org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(in)) {
            var sheet = wb.getSheetAt(0);
            var header = sheet.getRow(1);
            assertTrue(header.getLastCellNum() >= 55);
            for (int i = 0; i < 52; i++) {
                assertEquals(EmployeeRosterImportExcelVO.TEMPLATE_HEADERS[i], header.getCell(i).getStringCellValue(),
                        "template column " + i);
            }
            assertEquals("任职单位", header.getCell(52).getStringCellValue());
            assertEquals("任职部门", header.getCell(53).getStringCellValue());
            assertEquals("员工工号", header.getCell(54).getStringCellValue());
        }
    }

    @Test
    void toSaveReqMapsCoreFieldsAndContracts() {
        EmployeeRosterImportExcelVO row = EmployeeRosterImportExcelVO.builder()
                .name("钟伟")
                .idCard("430981198311201111")
                .mobile("15995408684")
                .sex("男")
                .companyName("文枢")
                .deptName("总经办")
                .jobPost("副总经理")
                .entryDate("2017-03-20")
                .formalDate("2017-03-20")
                .socialSecurityEnabled("是")
                .housingFundEnabled("是")
                .employeeType("正式")
                .employmentForm("全日制")
                .contract1Range("2017/03/20-2020/03/19")
                .contract2Range("2018/09/01-2021/08/31")
                .contract3Range("2021/09/01-2024/08/31")
                .contract4Range("2024/09/01-无固定期限")
                .currentContractType("无固定期限劳动合同")
                .emergencyContactRelation("高丹")
                .emergencyPhone("18216275527")
                .probationSalary(new BigDecimal("8000"))
                .build();

        EmployeeSaveReqVO req = EmployeeRosterImportSupport.toSaveReq(row);
        assertNull(req.getEmployeeNo());
        assertTrue(req.isSkipAutoEmployeeNo());
        assertEquals("钟伟", req.getName());
        assertEquals("430981198311201111", req.getIdCard());
        assertEquals("15995408684", req.getMobile());
        assertEquals(1, req.getSex());
        assertEquals(Boolean.TRUE, req.getSocialSecurityEnabled());
        assertEquals(LocalDate.of(2017, 3, 20), req.getEntryDate());
        assertNotNull(req.getContractList());
        assertEquals(4, req.getContractList().size());
        assertEquals(LocalDate.of(2017, 3, 20), req.getContractList().get(0).getStartDate());
        assertNull(req.getContractList().get(3).getEndDate()); // 无固定期限
        assertEquals("高丹", req.getEmergencyContact());
    }

    @Test
    void toSaveReqRejectsMissingIdCard() {
        EmployeeRosterImportExcelVO row = EmployeeRosterImportExcelVO.builder()
                .name("张三")
                .mobile("13800138000")
                .sex("女")
                .build();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> EmployeeRosterImportSupport.toSaveReq(row));
        assertTrue(ex.getMessage().contains("身份证号"));
    }

    @Test
    void importCreatesWhenIdCardAbsentAndUpdatesWhenPresent() {
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean(EmployeeServiceImpl.class)).thenReturn(employeeService);

            EmployeeRosterImportExcelVO createRow = baseRow("110101199001011234", "新建员");
            EmployeeRosterImportExcelVO updateRow = baseRow("110101199001011235", "更新员");
            EmployeeRosterImportExcelVO failRow = baseRow(null, "失败员");

            when(employeeArchiveMapper.selectByIdCard("110101199001011234")).thenReturn(null);
            EmployeeDO existing = new EmployeeDO();
            existing.setId(99L);
            existing.setEmployeeNo("10000099");
            when(employeeArchiveMapper.selectByIdCard("110101199001011235")).thenReturn(existing);

            lenient().doAnswer(inv -> {
                EmployeeDO e = inv.getArgument(0);
                e.setId(100L);
                return 1;
            }).when(employeeArchiveMapper).insert(any(EmployeeDO.class));
            lenient().when(employeeArchiveMapper.selectMaxNumericEmployeeNo()).thenReturn("10000000");
            lenient().when(employeeArchiveMapper.selectById(99L)).thenReturn(existing);

            EmployeeRosterImportRespVO resp = employeeService.importEmployeeRosterList(
                    List.of(createRow, updateRow, failRow));

            assertEquals(1, resp.getCreateNames().size());
            assertEquals("新建员", resp.getCreateNames().get(0));
            assertEquals(1, resp.getUpdateNames().size());
            assertEquals("更新员", resp.getUpdateNames().get(0));
            assertTrue(resp.getFailureRows().containsKey(5)); // 第 3 条数据 → Excel 行 5
            assertTrue(resp.getFailureRows().get(5).contains("身份证号"));

            ArgumentCaptor<EmployeeDO> insertCap = ArgumentCaptor.forClass(EmployeeDO.class);
            verify(employeeArchiveMapper, atLeastOnce()).insert(insertCap.capture());
            assertEquals("新建员", insertCap.getValue().getName());
            assertNull(insertCap.getValue().getEmployeeNo());
            verify(employeeArchiveMapper, never()).selectMaxNumericEmployeeNo();
        }
    }

    @Test
    void importWritesEmployeeNoWhenPresentAndKeepsExistingWhenBlank() {
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean(EmployeeServiceImpl.class)).thenReturn(employeeService);

            EmployeeRosterImportExcelVO createRow = baseRow("110101199001011301", "有工号员");
            createRow.setEmployeeNo("WS1001");
            EmployeeRosterImportExcelVO updateRow = baseRow("110101199001011302", "改工号员");
            updateRow.setEmployeeNo("WS2002");
            EmployeeRosterImportExcelVO keepRow = baseRow("110101199001011303", "保留工号员");

            when(employeeArchiveMapper.selectByIdCard("110101199001011301")).thenReturn(null);
            EmployeeDO existingChange = new EmployeeDO();
            existingChange.setId(301L);
            existingChange.setEmployeeNo("OLD001");
            when(employeeArchiveMapper.selectByIdCard("110101199001011302")).thenReturn(existingChange);
            when(employeeArchiveMapper.selectById(301L)).thenReturn(existingChange);
            EmployeeDO existingKeep = new EmployeeDO();
            existingKeep.setId(302L);
            existingKeep.setEmployeeNo("KEEP001");
            when(employeeArchiveMapper.selectByIdCard("110101199001011303")).thenReturn(existingKeep);
            when(employeeArchiveMapper.selectById(302L)).thenReturn(existingKeep);
            doAnswer(inv -> {
                EmployeeDO e = inv.getArgument(0);
                e.setId(300L);
                return 1;
            }).when(employeeArchiveMapper).insert(any(EmployeeDO.class));

            employeeService.importEmployeeRosterList(List.of(createRow, updateRow, keepRow));

            ArgumentCaptor<EmployeeDO> insertCap = ArgumentCaptor.forClass(EmployeeDO.class);
            verify(employeeArchiveMapper).insert(insertCap.capture());
            assertEquals("WS1001", insertCap.getValue().getEmployeeNo());

            ArgumentCaptor<EmployeeDO> updateCap = ArgumentCaptor.forClass(EmployeeDO.class);
            verify(employeeArchiveMapper, atLeast(2)).updateById(updateCap.capture());
            assertTrue(updateCap.getAllValues().stream()
                    .anyMatch(e -> "WS2002".equals(e.getEmployeeNo())));
            assertTrue(updateCap.getAllValues().stream()
                    .anyMatch(e -> "KEEP001".equals(e.getEmployeeNo())));
        }
    }

    @Test
    void blankEmployeeTypeDoesNotSetStatusOnSaveReq_andCreateDefaultsFormal() {
        EmployeeRosterImportExcelVO blankType = EmployeeRosterImportExcelVO.builder()
                .name("试用保留")
                .idCard("110101199001019999")
                .mobile("13900001111")
                .sex("女")
                .employeeType("  ")
                .build();
        EmployeeSaveReqVO req = EmployeeRosterImportSupport.toSaveReq(blankType);
        assertNull(req.getEmployeeStatus(), "F1: blank type must not force FORMAL on VO");

        assertEquals(EmployeeStatusEnum.FORMAL.getStatus(),
                EmployeeRosterImportSupport.defaultEmployeeStatusForCreate(null));
        assertEquals(EmployeeStatusEnum.PROBATIONARY.getStatus(),
                EmployeeRosterImportSupport.defaultEmployeeStatusForCreate(
                        EmployeeStatusEnum.PROBATIONARY.getStatus()));
    }

    @Test
    void importUpdateWithBlankEmployeeTypeKeepsExistingStatus() {
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean(EmployeeServiceImpl.class)).thenReturn(employeeService);

            String idCard = "110101199001018888";
            EmployeeRosterImportExcelVO row = EmployeeRosterImportExcelVO.builder()
                    .name("试用员工甲")
                    .idCard(idCard)
                    .mobile("13900002222")
                    .sex("男")
                    .deptName(DEPT_NAME)
                    .employeeType(null) // blank
                    .build();

            EmployeeDO existing = new EmployeeDO();
            existing.setId(55L);
            existing.setEmployeeNo("10000055");
            existing.setEmployeeStatus(EmployeeStatusEnum.PROBATIONARY.getStatus());
            existing.setName("旧名");
            when(employeeArchiveMapper.selectByIdCard(idCard)).thenReturn(existing);
            when(employeeArchiveMapper.selectById(55L)).thenReturn(existing);

            EmployeeRosterImportRespVO resp =
                    employeeService.importEmployeeRosterList(List.of(row));
            assertEquals(1, resp.getUpdateNames().size());
            assertTrue(resp.getFailureRows().isEmpty());

            ArgumentCaptor<EmployeeDO> updateCap = ArgumentCaptor.forClass(EmployeeDO.class);
            verify(employeeArchiveMapper, atLeastOnce()).updateById(updateCap.capture());
            assertEquals(EmployeeStatusEnum.PROBATIONARY.getStatus(),
                    updateCap.getAllValues().get(0).getEmployeeStatus(),
                    "F1: update must keep probationary when Excel 员工类型 blank");
        }
    }

    @Test
    void duplicateIdCardInFileIsMaskedInFailureAndLogs() {
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean(EmployeeServiceImpl.class)).thenReturn(employeeService);

            String idCard = "110101199001017777";
            EmployeeRosterImportExcelVO r1 = baseRow(idCard, "甲");
            EmployeeRosterImportExcelVO r2 = baseRow(idCard, "乙");

            when(employeeArchiveMapper.selectByIdCard(idCard)).thenReturn(null);
            lenient().doAnswer(inv -> {
                EmployeeDO e = inv.getArgument(0);
                e.setId(1L);
                return 1;
            }).when(employeeArchiveMapper).insert(any(EmployeeDO.class));
            lenient().when(employeeArchiveMapper.selectMaxNumericEmployeeNo()).thenReturn("10000000");

            Logger logger = (Logger) LoggerFactory.getLogger(EmployeeServiceImpl.class);
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();
            logger.addAppender(appender);
            try {
                EmployeeRosterImportRespVO resp =
                        employeeService.importEmployeeRosterList(List.of(r1, r2));
                assertEquals(1, resp.getCreateNames().size());
                assertEquals(1, resp.getFailureRows().size());
                String fail = resp.getFailureRows().get(4); // second data row = excel 4
                assertNotNull(fail);
                assertFalse(fail.contains(idCard), "F2: failure detail must not contain full idCard");
                assertTrue(fail.contains("7777") || fail.contains("****"),
                        "F2: masked form should retain last4 or stars");

                boolean logLeak = appender.list.stream()
                        .map(ILoggingEvent::getFormattedMessage)
                        .anyMatch(m -> m != null && m.contains(idCard));
                assertFalse(logLeak, "F2: warn log must not contain full idCard");
            } finally {
                logger.detachAppender(appender);
            }
        }
    }

    @Test
    void maskIdCardHelpers() {
        assertEquals("**************1234",
                EmployeeRosterImportSupport.maskIdCard("110101199001011234"));
        assertEquals("(empty)", EmployeeRosterImportSupport.maskIdCard(null));
        String full = "证号 110101199001011234 重复";
        assertFalse(EmployeeRosterImportSupport.sanitizeReasonForLog(full, "110101199001011234")
                .contains("110101199001011234"));
    }

    @Test
    void humanizeImportFailureMapsUnknownColumnToSchemaHint() {
        RuntimeException sql = new RuntimeException(
                "### Error querying database. Cause: java.sql.SQLSyntaxErrorException: "
                        + "Unknown column 'emergency_relationship' in 'field list'");
        String msg = EmployeeRosterImportSupport.humanizeImportFailure(sql, "110101199001011234");
        assertTrue(msg.contains("系统数据表结构异常"));
        assertFalse(msg.contains("emergency_relationship"));
        assertFalse(msg.contains("110101199001011234"));
    }

    @Test
    void humanizeImportFailureKeepsFieldValidationMessage() {
        IllegalArgumentException ex = new IllegalArgumentException("身份证号不能为空");
        assertEquals("身份证号不能为空",
                EmployeeRosterImportSupport.humanizeImportFailure(ex, null));
    }

    @Test
    void parseDateFailureIncludesCorrectFormatExample() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> EmployeeRosterImportSupport.parseDate("not-a-date", "入职日期"));
        assertTrue(ex.getMessage().contains("入职日期"));
        assertTrue(ex.getMessage().contains("正确示例"));
        assertTrue(ex.getMessage().contains("2024-01-15"));
    }

    @Test
    void normalizeYearMonthAcceptsFullDatesAndExcelSerial() {
        // Excel 日期格常被读成完整日或带时间
        assertEquals("2024-01", EmployeeRosterImportSupport.normalizeYearMonth("2024-01-01"));
        assertEquals("2024-01", EmployeeRosterImportSupport.normalizeYearMonth("2024/1/1"));
        assertEquals("2024-01", EmployeeRosterImportSupport.normalizeYearMonth("2024.01.01"));
        assertEquals("2024-01", EmployeeRosterImportSupport.normalizeYearMonth("2024年1月1日"));
        assertEquals("2024-01", EmployeeRosterImportSupport.normalizeYearMonth("2024-01-01 00:00:00"));
        assertEquals("2024-01", EmployeeRosterImportSupport.normalizeYearMonth("2024-01-01T00:00:00"));
        assertEquals("2024-01", EmployeeRosterImportSupport.normalizeYearMonth("2024-01"));
        assertEquals("2024-01", EmployeeRosterImportSupport.normalizeYearMonth("202401"));
        assertEquals("2024-01", EmployeeRosterImportSupport.normalizeYearMonth("20240101"));
        // Excel 序列日 2024-01-01 ≈ 45292（1899-12-30 纪元）
        assertEquals("2024-01", EmployeeRosterImportSupport.normalizeYearMonth("45292"));
        assertNull(EmployeeRosterImportSupport.normalizeYearMonth("  "));
        assertNull(EmployeeRosterImportSupport.normalizeYearMonth("not-a-month"));
    }

    @Test
    void toSaveReqAcceptsExcelDateCellAsSocialSecurityMonth() {
        EmployeeRosterImportExcelVO row = EmployeeRosterImportExcelVO.builder()
                .name("参保日期格")
                .idCard("110101199001016666")
                .mobile("13800006666")
                .sex("男")
                .socialSecurityEnabled("是")
                .socialSecurityStartMonth("2024-01-01") // EasyExcel 日期格常见形态
                .build();
        EmployeeSaveReqVO req = EmployeeRosterImportSupport.toSaveReq(row);
        assertEquals(Boolean.TRUE, req.getSocialSecurityEnabled());
        assertEquals("2024-01", req.getSocialSecurityStartMonth());
    }

    @Test
    void compactContractRangeWithOpenEndParses() {
        LocalDate[] se = EmployeeRosterImportSupport.parseDateRange("20210903-无固定期限");
        assertNotNull(se);
        assertEquals(LocalDate.of(2021, 9, 3), se[0]);
        assertNull(se[1]);
    }

    @Test
    void toSaveReqAcceptsCompactOpenEndedContractAndReportsExampleOnBadRange() {
        EmployeeRosterImportExcelVO ok = EmployeeRosterImportExcelVO.builder()
                .name("合同紧凑")
                .idCard("110101199001017777")
                .mobile("13800007777")
                .sex("男")
                .contract1Range("20210903-无固定期限")
                .build();
        EmployeeSaveReqVO req = EmployeeRosterImportSupport.toSaveReq(ok);
        assertEquals(1, req.getContractList().size());
        assertEquals(LocalDate.of(2021, 9, 3), req.getContractList().get(0).getStartDate());
        assertNull(req.getContractList().get(0).getEndDate());

        EmployeeRosterImportExcelVO bad = EmployeeRosterImportExcelVO.builder()
                .name("合同坏")
                .idCard("110101199001017778")
                .mobile("13800007778")
                .sex("女")
                .contract1Range("完全不是日期")
                .build();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> EmployeeRosterImportSupport.toSaveReq(bad));
        assertTrue(ex.getMessage().contains("正确示例"));
        assertTrue(ex.getMessage().contains("无固定期限") || ex.getMessage().contains("2021"));
    }

    @Test
    void concurrentCreateDuplicateKeyFallsBackToUpdate() {
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean(EmployeeServiceImpl.class)).thenReturn(employeeService);

            String idCard = "110101199001016666";
            EmployeeRosterImportExcelVO row = baseRow(idCard, "并发员");

            EmployeeDO winner = new EmployeeDO();
            winner.setId(77L);
            winner.setEmployeeNo("10000077");
            winner.setEmployeeStatus(EmployeeStatusEnum.INTERN.getStatus());

            // first select: not found → try create; insert throws UK; re-select finds winner
            when(employeeArchiveMapper.selectByIdCard(idCard))
                    .thenReturn(null)
                    .thenReturn(winner);
            lenient().when(employeeArchiveMapper.selectMaxNumericEmployeeNo()).thenReturn("10000000");
            doThrow(new DuplicateKeyException("uk_hrm_employee_active_id_card"))
                    .when(employeeArchiveMapper).insert(any(EmployeeDO.class));
            when(employeeArchiveMapper.selectById(77L)).thenReturn(winner);

            EmployeeRosterImportRespVO resp =
                    employeeService.importEmployeeRosterList(List.of(row));
            assertEquals(0, resp.getCreateNames().size());
            assertEquals(1, resp.getUpdateNames().size());
            assertTrue(resp.getFailureRows().isEmpty());
            verify(employeeArchiveMapper, atLeastOnce()).updateById(any(EmployeeDO.class));
        }
    }

    /**
     * P1-A 组合回归：空白员工类型 + 并发 UK 冲突回退 update；
     * 获胜行为试用期时不得被写成正式。
     */
    @Test
    void blankEmployeeTypeOnConcurrentConflictKeepsWinnerNonFormalStatus() {
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean(EmployeeServiceImpl.class)).thenReturn(employeeService);

            String idCard = "110101199001015555";
            EmployeeRosterImportExcelVO row = EmployeeRosterImportExcelVO.builder()
                    .name("空白冲突员")
                    .idCard(idCard)
                    .mobile("13900005555")
                    .sex("女")
                    .deptName(DEPT_NAME)
                    .employeeType(null) // 空白
                    .build();

            EmployeeDO winner = new EmployeeDO();
            winner.setId(88L);
            winner.setEmployeeNo("10000088");
            winner.setEmployeeStatus(EmployeeStatusEnum.PROBATIONARY.getStatus());
            winner.setName("先写入试用");

            when(employeeArchiveMapper.selectByIdCard(idCard))
                    .thenReturn(null)
                    .thenReturn(winner);
            lenient().when(employeeArchiveMapper.selectMaxNumericEmployeeNo()).thenReturn("10000000");
            // create 路径会先 default 为正式再 insert；冲突后必须恢复 null 再 update
            doThrow(new DuplicateKeyException("uk_hrm_employee_active_id_card"))
                    .when(employeeArchiveMapper).insert(any(EmployeeDO.class));
            when(employeeArchiveMapper.selectById(88L)).thenReturn(winner);

            EmployeeRosterImportRespVO resp =
                    employeeService.importEmployeeRosterList(List.of(row));
            assertEquals(1, resp.getUpdateNames().size());
            assertTrue(resp.getFailureRows().isEmpty());

            ArgumentCaptor<EmployeeDO> updateCap = ArgumentCaptor.forClass(EmployeeDO.class);
            verify(employeeArchiveMapper, atLeastOnce()).updateById(updateCap.capture());
            assertEquals(EmployeeStatusEnum.PROBATIONARY.getStatus(),
                    updateCap.getAllValues().get(0).getEmployeeStatus(),
                    "P1-A: blank type + conflict fallback must keep winner probationary");
            assertNotEquals(EmployeeStatusEnum.FORMAL.getStatus(),
                    updateCap.getValue().getEmployeeStatus());
        }
    }

    @Test
    void officialTemplateXlsxDataRowsAreReadableWithHeadRow2() throws Exception {
        ClassPathResource resource = new ClassPathResource("excel/文枢花名册导入模板.xlsx");
        assertTrue(resource.exists());
        byte[] bytes;
        try (InputStream in = resource.getInputStream()) {
            bytes = in.readAllBytes();
        }
        List<EmployeeRosterImportExcelVO> rows =
                ExcelUtils.read(bytes, EmployeeRosterImportExcelVO.class, 2);
        // 官方模板含示例数据行
        assertFalse(rows.isEmpty(), "F4: template should yield data rows under headRowNumber=2");
        EmployeeRosterImportExcelVO first = rows.get(0);
        assertNotNull(first.getName());
        assertNotNull(first.getIdCard());
        // 映射不抛
        EmployeeSaveReqVO req = EmployeeRosterImportSupport.toSaveReq(first);
        assertEquals(first.getName().trim(), req.getName());
        assertNotNull(req.getIdCard());
        assertNotNull(req.getSex());
    }

    private static EmployeeRosterImportExcelVO baseRow(String idCard, String name) {
        return EmployeeRosterImportExcelVO.builder()
                .name(name)
                .idCard(idCard)
                .mobile("13800138000")
                .sex("男")
                .employeeType("正式")
                .deptName(DEPT_NAME)
                .build();
    }

    @Test
    void importBindsDeptIdAndCompanyFromDeptName() {
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean(EmployeeServiceImpl.class)).thenReturn(employeeService);

            EmployeeRosterImportExcelVO row = baseRow("110101199001011001", "部门绑定员");
            when(employeeArchiveMapper.selectByIdCard("110101199001011001")).thenReturn(null);
            doAnswer(inv -> {
                EmployeeDO e = inv.getArgument(0);
                e.setId(201L);
                return 1;
            }).when(employeeArchiveMapper).insert(any(EmployeeDO.class));
            lenient().when(employeeArchiveMapper.selectMaxNumericEmployeeNo()).thenReturn("10000000");

            EmployeeRosterImportRespVO resp = employeeService.importEmployeeRosterList(List.of(row));
            assertEquals(1, resp.getCreateNames().size());
            assertTrue(resp.getFailureRows().isEmpty());

            ArgumentCaptor<EmployeeDO> cap = ArgumentCaptor.forClass(EmployeeDO.class);
            verify(employeeArchiveMapper).insert(cap.capture());
            assertEquals(DEPT_ID, cap.getValue().getDeptId());
            assertEquals(DEPT_NAME, cap.getValue().getDeptName());
            assertEquals(COMPANY_ID, cap.getValue().getCompanyId());
        }
    }

    @Test
    void importFailsWhenDeptMissing() {
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean(EmployeeServiceImpl.class)).thenReturn(employeeService);

            EmployeeRosterImportExcelVO row = EmployeeRosterImportExcelVO.builder()
                    .name("无部门员")
                    .idCard("110101199001011002")
                    .mobile("13800138001")
                    .sex("男")
                    .deptName("  ")
                    .build();

            EmployeeRosterImportRespVO resp = employeeService.importEmployeeRosterList(List.of(row));
            assertTrue(resp.getCreateNames().isEmpty());
            assertTrue(resp.getFailureRows().get(3).contains("部门不能为空"));
            verify(employeeArchiveMapper, never()).insert(any(EmployeeDO.class));
        }
    }

    @Test
    void importFailsWhenDeptNameUnknown() {
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean(EmployeeServiceImpl.class)).thenReturn(employeeService);

            EmployeeRosterImportExcelVO row = baseRow("110101199001011003", "未知部门员");
            row.setDeptName("不存在的部门XYZ");

            EmployeeRosterImportRespVO resp = employeeService.importEmployeeRosterList(List.of(row));
            assertTrue(resp.getCreateNames().isEmpty());
            String fail = resp.getFailureRows().get(3);
            assertNotNull(fail);
            assertTrue(fail.contains("部门不存在") || fail.contains("未启用"));
            assertTrue(fail.contains("不存在的部门XYZ"));
            verify(employeeArchiveMapper, never()).insert(any(EmployeeDO.class));
        }
    }

    @Test
    void importFailsWhenDeptNameAmbiguous() {
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean(EmployeeServiceImpl.class)).thenReturn(employeeService);

            DeptRespDTO d1 = new DeptRespDTO();
            d1.setId(11L);
            d1.setName("研发部");
            d1.setStatus(0);
            DeptRespDTO d2 = new DeptRespDTO();
            d2.setId(12L);
            d2.setName("研发部");
            d2.setStatus(0);
            when(deptApi.getSimpleDeptList()).thenReturn(CommonResult.success(List.of(d1, d2)));

            EmployeeRosterImportExcelVO row = baseRow("110101199001011004", "重名部门员");
            row.setDeptName("研发部");

            EmployeeRosterImportRespVO resp = employeeService.importEmployeeRosterList(List.of(row));
            assertTrue(resp.getCreateNames().isEmpty());
            assertTrue(resp.getFailureRows().get(3).contains("多个匹配"));
        }
    }

    @Test
    void importBindsDeptUnderCompanyNameWhenDeptNameDuplicates() {
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean(EmployeeServiceImpl.class)).thenReturn(employeeService);

            DeptRespDTO d1 = new DeptRespDTO();
            d1.setId(11L);
            d1.setName("财务部");
            d1.setParentId(100L);
            d1.setOrgType("0");
            d1.setStatus(0);
            DeptRespDTO d2 = new DeptRespDTO();
            d2.setId(12L);
            d2.setName("财务部");
            d2.setParentId(200L);
            d2.setOrgType("0");
            d2.setStatus(0);
            DeptRespDTO c1 = new DeptRespDTO();
            c1.setId(100L);
            c1.setName("文枢科技");
            c1.setOrgType("1");
            c1.setParentId(0L);
            c1.setStatus(0);
            DeptRespDTO c2 = new DeptRespDTO();
            c2.setId(200L);
            c2.setName("另一家公司");
            c2.setOrgType("1");
            c2.setParentId(0L);
            c2.setStatus(0);
            when(deptApi.getSimpleDeptList()).thenReturn(CommonResult.success(List.of(d1, d2, c1, c2)));
            when(deptApi.getDept(11L)).thenReturn(CommonResult.success(d1));
            when(deptApi.getDept(12L)).thenReturn(CommonResult.success(d2));
            when(deptApi.getDept(100L)).thenReturn(CommonResult.success(c1));
            when(deptApi.getDept(200L)).thenReturn(CommonResult.success(c2));

            EmployeeRosterImportExcelVO row = baseRow("110101199001011005", "消歧员");
            row.setDeptName("财务部");
            row.setCompanyName("文枢科技");
            when(employeeArchiveMapper.selectByIdCard("110101199001011005")).thenReturn(null);
            doAnswer(inv -> {
                EmployeeDO e = inv.getArgument(0);
                e.setId(301L);
                return 1;
            }).when(employeeArchiveMapper).insert(any(EmployeeDO.class));
            lenient().when(employeeArchiveMapper.selectMaxNumericEmployeeNo()).thenReturn("10000000");

            EmployeeRosterImportRespVO resp = employeeService.importEmployeeRosterList(List.of(row));
            assertEquals(List.of("消歧员"), resp.getCreateNames());
            assertTrue(resp.getFailureRows().isEmpty());
            ArgumentCaptor<EmployeeDO> captor = ArgumentCaptor.forClass(EmployeeDO.class);
            verify(employeeArchiveMapper).insert(captor.capture());
            assertEquals(11L, captor.getValue().getDeptId());
            assertEquals(100L, captor.getValue().getCompanyId());
        }
    }

    @Test
    void getEmployeeArchiveFillsDeptAndCompanyNamesForEdit() {
        EmployeeDO archive = new EmployeeDO();
        archive.setId(9L);
        archive.setName("编辑员");
        archive.setSex(1);
        archive.setEmployeeStatus(1);
        archive.setDeptId(DEPT_ID);
        archive.setCompanyId(null);
        archive.setDeptName(null);
        archive.setCompanyName(null);
        when(employeeArchiveMapper.selectById(9L)).thenReturn(archive);
        when(employeeWorkExperienceMapper.selectListByEmployeeId(9L)).thenReturn(List.of());
        when(employeeEducationMapper.selectListByEmployeeId(9L)).thenReturn(List.of());
        when(employeeFamilyMapper.selectListByEmployeeId(9L)).thenReturn(List.of());
        when(employeeContractMapper.selectListByEmployeeId(9L)).thenReturn(List.of());
        when(attachmentService.getAttachmentListByBusinessInternal(any(), anyLong()))
                .thenReturn(List.of());

        var resp = employeeService.getEmployeeArchive(9L);
        assertNotNull(resp);
        assertEquals(DEPT_ID, resp.getDeptId());
        assertEquals(DEPT_NAME, resp.getDeptName());
        assertEquals(COMPANY_ID, resp.getCompanyId());
        assertEquals("文枢科技", resp.getCompanyName());
    }

    @Test
    void importWritesExtraUnsignedEmployment() {
        DeptRespDTO extraDept = new DeptRespDTO();
        extraDept.setId(2002L);
        extraDept.setName("财务部");
        extraDept.setParentId(200L);
        extraDept.setStatus(0);
        extraDept.setOrgType("0");
        DeptRespDTO extraCompany = new DeptRespDTO();
        extraCompany.setId(200L);
        extraCompany.setName("另一家公司");
        extraCompany.setOrgType("1");
        extraCompany.setStatus(0);
        extraCompany.setParentId(0L);
        DeptRespDTO signedDept = new DeptRespDTO();
        signedDept.setId(DEPT_ID);
        signedDept.setName(DEPT_NAME);
        signedDept.setParentId(COMPANY_ID);
        signedDept.setStatus(0);
        signedDept.setOrgType("0");
        when(deptApi.getSimpleDeptList()).thenReturn(CommonResult.success(List.of(signedDept, extraDept)));
        when(deptApi.getDept(2002L)).thenReturn(CommonResult.success(extraDept));
        when(deptApi.getDept(200L)).thenReturn(CommonResult.success(extraCompany));

        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean(EmployeeServiceImpl.class)).thenReturn(employeeService);
            EmployeeRosterImportExcelVO row = baseRow("110101199001011088", "多任职员");
            row.setExtraCompanyNames("另一家公司");
            row.setExtraDeptNames("财务部");
            when(employeeArchiveMapper.selectByIdCard("110101199001011088")).thenReturn(null);
            doAnswer(inv -> {
                EmployeeDO e = inv.getArgument(0);
                e.setId(88L);
                return 1;
            }).when(employeeArchiveMapper).insert(any(EmployeeDO.class));
            lenient().when(employeeArchiveMapper.selectMaxNumericEmployeeNo()).thenReturn("10000000");

            EmployeeRosterImportRespVO resp = employeeService.importEmployeeRosterList(List.of(row));
            assertEquals(List.of("多任职员"), resp.getCreateNames());
            assertTrue(resp.getFailureRows().isEmpty());
            verify(employeeEmploymentMapper, times(2)).insert(any(cn.iocoder.yudao.module.hrm.dal.dataobject.employee.EmployeeEmploymentDO.class));
        }
    }
}
