package cn.iocoder.yudao.module.hrm.service.employee;

import cn.hutool.extra.spring.SpringUtil;
import cn.idev.excel.annotation.ExcelProperty;
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
import cn.iocoder.yudao.common.server.attachment.service.AttachmentService;
import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import cn.iocoder.yudao.module.infra.api.file.FileAccessApi;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

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
import static org.mockito.Mockito.*;

/**
 * 文枢花名册导入：表头与官方附件对齐 + 映射/upsert 行为。
 */
@ExtendWith(MockitoExtension.class)
class EmployeeRosterImportTest {

    private static final String EXPECTED_TEMPLATE_SHA256 =
            "79ce40ae8aa0c584b58fae2da02def6d8032737a34cd81cb35a87642c81bf540";

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
        assertEquals(52, annotated.size());
        for (int i = 0; i < 52; i++) {
            assertEquals(EmployeeRosterImportExcelVO.TEMPLATE_HEADERS[i], annotated.get(i),
                    "column index " + i);
        }

        // 关键列
        assertEquals("最高学历\n毕业学校", annotated.get(27));
        assertEquals("第一学历\n毕业学校", annotated.get(30));
        assertTrue(annotated.get(51).startsWith("入职资料（系统里可以标注"));
        assertEquals(
                "入职资料（系统里可以标注一个上传附件的地方，我们可以扫描上传入职资料）",
                annotated.get(51));
    }

    @Test
    void bundledImportTemplateSha256MatchesAuthoritativeAttachment() throws Exception {
        ClassPathResource resource = new ClassPathResource("excel/文枢花名册导入模板.xlsx");
        assertTrue(resource.exists(), "classpath template missing");
        byte[] bytes;
        try (InputStream in = resource.getInputStream()) {
            bytes = in.readAllBytes();
        }
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        String sha = HexFormat.of().formatHex(md.digest(bytes));
        assertEquals(EXPECTED_TEMPLATE_SHA256, sha);

        // 工作区附件（若存在）亦对齐
        Path workspaceTpl = Path.of("attachments/文枢花名册模版.xlsx");
        if (Files.isRegularFile(workspaceTpl)) {
            String sha2 = HexFormat.of().formatHex(md.digest(Files.readAllBytes(workspaceTpl)));
            // re-init digest
            sha2 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(workspaceTpl)));
            assertEquals(EXPECTED_TEMPLATE_SHA256, sha2);
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
            lenient().when(employeeArchiveMapper.selectMaxEmployeeNo()).thenReturn(10000000L);
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
        }
    }

    private static EmployeeRosterImportExcelVO baseRow(String idCard, String name) {
        return EmployeeRosterImportExcelVO.builder()
                .name(name)
                .idCard(idCard)
                .mobile("13800138000")
                .sex("男")
                .employeeType("正式")
                .build();
    }
}
