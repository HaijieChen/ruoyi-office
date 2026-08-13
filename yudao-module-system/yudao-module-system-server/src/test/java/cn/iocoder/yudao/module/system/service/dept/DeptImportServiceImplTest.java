package cn.iocoder.yudao.module.system.service.dept;

import cn.idev.excel.FastExcelFactory;
import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptImportExcelVO;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptImportRespVO;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptListReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.dept.DeptMapper;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import jakarta.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.DEPT_IMPORT_FILE_CHANGED;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.DEPT_IMPORT_FILE_TYPE;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.DEPT_IMPORT_NO_FULL_DATA_SCOPE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * {@link DeptImportServiceImpl} 单元测试
 */
@Import({DeptImportServiceImpl.class, DeptServiceImpl.class, DeptMutationLock.class})
public class DeptImportServiceImplTest extends BaseDbUnitTest {

    @Resource
    private DeptImportService deptImportService;
    @Resource
    private DeptMapper deptMapper;

    @MockitoBean
    private PermissionService permissionService;
    @MockitoBean
    private AdminUserService adminUserService;

    @BeforeEach
    void stubPermissionAndUser() {
        when(permissionService.getDeptDataPermission(anyLong()))
                .thenReturn(new DeptDataPermissionRespDTO().setAll(true));
        AdminUserDO leader = new AdminUserDO();
        leader.setId(9001L);
        leader.setUsername("leader1");
        leader.setStatus(CommonStatusEnum.ENABLE.getStatus());
        when(adminUserService.getUserByUsername("leader1")).thenReturn(leader);
    }

    @Test
    void templateHeadersMatchContract() throws Exception {
        // 表头契约：与 DeptImportExcelVO @ExcelProperty 一致，可被 FastExcel 写出再读回
        List<DeptImportExcelVO> sample = List.of(DeptImportExcelVO.builder()
                .name("文枢科技").parentPath("").orgTypeLabel("公司").sortText("0")
                .statusLabel("启用").functionalCurrency("CNY").build());
        byte[] bytes = writeExcel(sample);
        MockMultipartFile file = xlsxFile(bytes);
        try (MockedStatic<?> login = mockLogin()) {
            DeptImportRespVO resp = deptImportService.validateImport(file);
            assertTrue(resp.getCanCommit());
            assertEquals(1, resp.getCreateCount());
            assertEquals(0, resp.getSkipCount());
            assertNotNull(resp.getFileDigest());
        }
    }

    @Test
    void unorderedThreeLevelCreateAndIdempotentSkip() throws Exception {
        // 乱序：子先于父
        List<DeptImportExcelVO> rows = List.of(
                row("后端组", "文枢科技/研发中心", "部门", "1", "启用", null, null),
                row("研发中心", "文枢科技", "部门", "1", "启用", null, null),
                row("文枢科技", "", "公司", "0", "启用", "CNY", "leader1")
        );
        byte[] bytes = writeExcel(rows);
        try (MockedStatic<?> login = mockLogin()) {
            DeptImportRespVO preview = deptImportService.validateImport(xlsxFile(bytes));
            assertTrue(preview.getCanCommit(), () -> String.valueOf(preview.getErrors()));
            assertEquals(3, preview.getCreateCount());

            DeptImportRespVO committed = deptImportService.importDepts(xlsxFile(bytes), preview.getFileDigest());
            assertTrue(committed.getCanCommit());
            assertEquals(3, committed.getCreateCount());
            assertEquals(3, deptMapper.selectList(new DeptListReqVO()).size());

            // 重复导入：全跳过
            DeptImportRespVO again = deptImportService.importDepts(xlsxFile(bytes), preview.getFileDigest());
            assertTrue(again.getCanCommit());
            assertEquals(0, again.getCreateCount());
            assertEquals(3, again.getSkipCount());
            assertEquals(3, deptMapper.selectList(new DeptListReqVO()).size());
        }
    }

    @Test
    void existingConflictZeroWrite() throws Exception {
        DeptDO existing = new DeptDO();
        existing.setName("文枢科技");
        existing.setParentId(DeptDO.PARENT_ID_ROOT);
        existing.setSort(0);
        existing.setStatus(0);
        existing.setOrgType("1");
        existing.setFunctionalCurrency("CNY");
        deptMapper.insert(existing);
        List<DeptImportExcelVO> rows = List.of(
                row("文枢科技", "", "公司", "0", "启用", "USD", null) // 币种不同
        );
        byte[] bytes = writeExcel(rows);
        try (MockedStatic<?> login = mockLogin()) {
            DeptImportRespVO preview = deptImportService.validateImport(xlsxFile(bytes));
            assertFalse(preview.getCanCommit());
            assertTrue(preview.getErrors().stream().anyMatch(e -> "EXISTING_CONFLICT".equals(e.getCode())));

            long before = deptMapper.selectList(new DeptListReqVO()).size();
            DeptImportRespVO committed = deptImportService.importDepts(xlsxFile(bytes), preview.getFileDigest());
            assertFalse(committed.getCanCommit());
            assertEquals(before, deptMapper.selectList(new DeptListReqVO()).size());
        }
    }

    @Test
    void companyUnderDepartmentRejected() throws Exception {
        List<DeptImportExcelVO> rows = List.of(
                row("研发中心", "", "部门", "0", "启用", null, null),
                row("文枢子公司", "研发中心", "公司", "1", "启用", "CNY", null)
        );
        byte[] bytes = writeExcel(rows);
        try (MockedStatic<?> login = mockLogin()) {
            DeptImportRespVO preview = deptImportService.validateImport(xlsxFile(bytes));
            assertFalse(preview.getCanCommit());
            assertTrue(preview.getErrors().stream().anyMatch(e -> "PARENT_TYPE_INVALID".equals(e.getCode())));
        }
    }

    @Test
    void missingParentRejected() throws Exception {
        List<DeptImportExcelVO> rows = List.of(
                row("后端组", "不存在的父", "部门", "1", "启用", null, null)
        );
        byte[] bytes = writeExcel(rows);
        try (MockedStatic<?> login = mockLogin()) {
            DeptImportRespVO preview = deptImportService.validateImport(xlsxFile(bytes));
            assertFalse(preview.getCanCommit());
            assertTrue(preview.getErrors().stream().anyMatch(e -> "PARENT_NOT_FOUND".equals(e.getCode())));
        }
    }

    @Test
    void invalidCurrencyAndLeader() throws Exception {
        List<DeptImportExcelVO> rows = List.of(
                row("文枢科技", "", "公司", "0", "启用", "EUR", "no_such_user")
        );
        byte[] bytes = writeExcel(rows);
        try (MockedStatic<?> login = mockLogin()) {
            DeptImportRespVO preview = deptImportService.validateImport(xlsxFile(bytes));
            assertFalse(preview.getCanCommit());
            assertTrue(preview.getErrors().stream().anyMatch(e -> "CURRENCY_INVALID".equals(e.getCode())));
            assertTrue(preview.getErrors().stream().anyMatch(e -> "LEADER_NOT_FOUND".equals(e.getCode())));
        }
    }

    @Test
    void fileChangedRejected() throws Exception {
        byte[] bytes = writeExcel(List.of(row("A", "", "部门", "0", "启用", null, null)));
        try (MockedStatic<?> login = mockLogin()) {
            assertServiceException(
                    () -> deptImportService.importDepts(xlsxFile(bytes), "deadbeef"),
                    DEPT_IMPORT_FILE_CHANGED);
        }
    }

    @Test
    void rejectNonXlsx() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "org.xls", "application/vnd.ms-excel", new byte[]{1, 2, 3});
        try (MockedStatic<?> login = mockLogin()) {
            assertServiceException(() -> deptImportService.validateImport(file), DEPT_IMPORT_FILE_TYPE);
        }
    }

    @Test
    void rejectWithoutFullDataScope() throws Exception {
        when(permissionService.getDeptDataPermission(anyLong()))
                .thenReturn(new DeptDataPermissionRespDTO().setAll(false));
        byte[] bytes = writeExcel(List.of(row("A", "", "部门", "0", "启用", null, null)));
        try (MockedStatic<?> login = mockLogin()) {
            assertServiceException(() -> deptImportService.validateImport(xlsxFile(bytes)),
                    DEPT_IMPORT_NO_FULL_DATA_SCOPE);
        }
    }

    @Test
    void pathDuplicateInFile() throws Exception {
        List<DeptImportExcelVO> rows = List.of(
                row("同名", "", "部门", "0", "启用", null, null),
                row("同名", "", "部门", "1", "启用", null, null)
        );
        byte[] bytes = writeExcel(rows);
        try (MockedStatic<?> login = mockLogin()) {
            DeptImportRespVO preview = deptImportService.validateImport(xlsxFile(bytes));
            assertFalse(preview.getCanCommit());
            assertTrue(preview.getErrors().stream().anyMatch(e -> "PATH_DUPLICATE".equals(e.getCode())));
        }
    }

    private static MockedStatic<?> mockLogin() {
        MockedStatic<?> mocked = mockStatic(
                cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.class);
        mocked.when(() -> getLoginUserId()).thenReturn(1L);
        return mocked;
    }

    private static DeptImportExcelVO row(String name, String parent, String type, String sort,
                                         String status, String currency, String leader) {
        return DeptImportExcelVO.builder()
                .name(name).parentPath(parent).orgTypeLabel(type).sortText(sort)
                .statusLabel(status).functionalCurrency(currency).leaderUsername(leader)
                .build();
    }

    private static byte[] writeExcel(List<DeptImportExcelVO> rows) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FastExcelFactory.write(out, DeptImportExcelVO.class).sheet("组织架构").doWrite(rows);
        return out.toByteArray();
    }

    private static MockMultipartFile xlsxFile(byte[] bytes) {
        return new MockMultipartFile("file", "org.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }

}
