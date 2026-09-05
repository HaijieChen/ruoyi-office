package cn.iocoder.yudao.module.oa.service.seal;

import cn.iocoder.yudao.common.server.attachment.controller.vo.AttachmentSaveReqVO;
import cn.iocoder.yudao.common.server.attachment.service.AttachmentService;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.bill.BillCodeUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessStartApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.oa.controller.admin.seal.SealApplyBillController;
import cn.iocoder.yudao.module.oa.controller.admin.seal.vo.SealApplyBillSaveReqVO;
import cn.iocoder.yudao.module.oa.dal.dataobject.seal.SealApplyBillDO;
import cn.iocoder.yudao.module.oa.dal.mysql.seal.SealApplyBillMapper;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.datapermission.core.aop.DataPermissionContextHolder;
import cn.iocoder.yudao.framework.datapermission.core.db.DataPermissionRuleHandler;
import cn.iocoder.yudao.framework.datapermission.core.rule.DataPermissionRuleFactoryImpl;
import cn.iocoder.yudao.framework.datapermission.core.rule.dept.DeptDataPermissionRule;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.mybatis.spring.SqlSessionTemplate;
import org.apache.ibatis.annotations.Select;
import java.util.Set;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real Spring method-security and local transaction proxies; BPM transport is mocked, not distributed proof. */
class SealApplyBillStartTest {
    private AnnotationConfigApplicationContext context;
    private SealApplyBillController controller;
    private SealApplyBillMapper mapper;
    private BpmProcessInstanceApi bpm;
    private BpmProcessStartApi start;
    private JdbcTemplate jdbc;
    private MockedStatic<BillCodeUtils> codes;

    @Configuration
    @EnableMethodSecurity
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean SealApplyBillController controller() { return new SealApplyBillController(); }
        @Bean SealApplyBillService sealApplyBillService() { return new SealApplyBillServiceImpl(); }
        @Bean SealApplyBillMapper sealApplyBillMapper() { return mock(SealApplyBillMapper.class); }
        @Bean AttachmentService attachmentService() { return mock(AttachmentService.class); }
        @Bean BpmProcessInstanceApi processInstanceApi() { return mock(BpmProcessInstanceApi.class); }
        @Bean BpmProcessStartApi processStartApi() { return mock(BpmProcessStartApi.class); }
        @Bean AdminUserApi adminUserApi() { return mock(AdminUserApi.class); }
        @Bean DeptApi deptApi() { return mock(DeptApi.class); }
        @Bean DriverManagerDataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:seal-start;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean JdbcTemplate jdbcTemplate() { return new JdbcTemplate(dataSource()); }
        @Bean PlatformTransactionManager transactionManager() { return new DataSourceTransactionManager(dataSource()); }
        @Bean(name = "ss") PermissionStub permissions() { return new PermissionStub(); }
    }
    public static class PermissionStub {
        public boolean hasPermission(String permission) { return false; }
    }
    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext(Config.class);
        controller = context.getBean(SealApplyBillController.class);
        mapper = context.getBean(SealApplyBillMapper.class);
        bpm = context.getBean(BpmProcessInstanceApi.class);
        start = context.getBean(BpmProcessStartApi.class);
        jdbc = context.getBean(JdbcTemplate.class);
        jdbc.execute("CREATE TABLE IF NOT EXISTS seal_tx_probe(id BIGINT PRIMARY KEY)");
        jdbc.update("DELETE FROM seal_tx_probe");
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(42L).setTenantId(1L).setUserType(UserTypeEnum.ADMIN.getValue()), new org.springframework.mock.web.MockHttpServletRequest());
        TenantContextHolder.setTenantId(1L);
        codes = mockStatic(BillCodeUtils.class);
        codes.when(() -> BillCodeUtils.generateBillCode(any(), any())).thenReturn("SEAL-NEW");
        when(start.validateStart("oa_seal_apply_bill")).thenReturn(CommonResult.success(true));
        when(context.getBean(AdminUserApi.class).getUser(42L)).thenReturn(CommonResult.success(
                new AdminUserRespDTO().setId(42L).setNickname("Current user").setDeptId(20L)));
        when(context.getBean(DeptApi.class).getDept(20L)).thenReturn(CommonResult.success(
                new DeptRespDTO().setId(20L).setName("Current dept").setOrgType("2").setParentId(10L)));
        when(context.getBean(DeptApi.class).getDept(10L)).thenReturn(CommonResult.success(
                new DeptRespDTO().setId(10L).setName("Current company").setOrgType("1")));
        when(mapper.insert(any(SealApplyBillDO.class))).thenAnswer(invocation -> {
            SealApplyBillDO bill = invocation.getArgument(0);
            bill.setId(100L);
            jdbc.update("INSERT INTO seal_tx_probe(id) VALUES (?)", bill.getId());
            return 1;
        });
        when(bpm.createProcessInstance(eq(42L), any())).thenReturn(CommonResult.success("new-instance"));
    }
    @AfterEach void cleanup() {
        if (codes != null) codes.close();
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
        if (context != null) context.close();
    }
    private SealApplyBillSaveReqVO request() {
        SealApplyBillSaveReqVO req = new SealApplyBillSaveReqVO();
        req.setSealId(1L); req.setSealNo("S1"); req.setCause("Test");
        req.setUseType(1); req.setUseMode(1);
        req.setCompanyId(10L); req.setCompanyName("Untrusted name");
        req.setDeptId(20L); req.setDeptName("Untrusted name");
        return req;
    }
    @Test void methodSecurityAllowsNoCreateAuthorityAndServerDerivesIdentity() {
        SealApplyBillSaveReqVO req = request();
        req.setCreatorName("Forged name");
        assertEquals(100L, controller.createAndStartSealApplyBill(req).getCheckedData());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM seal_tx_probe", Integer.class));
        verify(bpm).createProcessInstance(eq(42L), argThat(dto ->
                "oa_seal_apply_bill".equals(dto.getProcessDefinitionKey()) && "100".equals(dto.getBusinessKey())));
        verify(bpm, never()).submitProcessInstance(any(), any());
        assertEquals("42", req.getCreator());
        assertEquals("Current user", req.getCreatorName());
        assertEquals("Current company", req.getCompanyName());
        assertEquals("Current dept", req.getDeptName());
    }
    @Test void freeTextSealNameStartsWithoutCatalogOrKeeperFields() {
        SealApplyBillSaveReqVO req = request();
        req.setSealId(null);
        req.setSealNo(null);
        req.setSealName("手输测试公章");
        req.setUseMode(2);
        req.setExpectedUseTime(LocalDateTime.of(2026, 9, 7, 10, 0));
        req.setExpectedReturnTime(LocalDateTime.of(2026, 9, 8, 10, 0));
        try (jakarta.validation.ValidatorFactory validator = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            assertTrue(validator.getValidator().validate(req).isEmpty());
        }
        assertEquals(100L, controller.createAndStartSealApplyBill(req).getCheckedData());
        verify(mapper).insert(argThat((SealApplyBillDO bill) ->
                "手输测试公章".equals(bill.getSealName()) && bill.getSealId() == null
                        && bill.getSealNo() == null && bill.getKeeperId() == null
                        && bill.getKeeperName() == null && bill.getKeeperDeptId() == null
                        && bill.getKeeperDeptName() == null));
        verify(bpm).createProcessInstance(eq(42L), any());
        verify(bpm, never()).submitProcessInstance(any(), any());
        assertEquals("42", req.getCreator());
    }
    @Test void oldEndpointsStillDenyWithoutCreatePermission() {
        assertThrows(AccessDeniedException.class, () -> controller.createSealApplyBill(request()));
        assertThrows(AccessDeniedException.class, () -> controller.saveSealApplyBill(request()));
        assertThrows(AccessDeniedException.class, () -> controller.submitSealApplyBill(request()));
        verifyNoInteractions(mapper, bpm);
    }
    @Test void anonymousDeniedByActualMethodSecurity() {
        SecurityContextHolder.clearContext();
        assertThrows(AuthenticationCredentialsNotFoundException.class,
                () -> controller.createAndStartSealApplyBill(request()));
        verifyNoInteractions(mapper, bpm, start);
    }
    @Test void forgedControlledFieldsRejectedBeforeAnyWrite() {
        List<Consumer<SealApplyBillSaveReqVO>> changes = List.of(
                req -> req.setId(8L), req -> req.setProcessInstanceId("old"),
                req -> req.setProcessStatus(2), req -> req.setUseStatus(1),
                req -> req.setBillCode("old-code"), req -> req.setActualUseTime(LocalDateTime.now()),
                req -> req.setActualReturnTime(LocalDateTime.now()));
        for (Consumer<SealApplyBillSaveReqVO> change : changes) {
            SealApplyBillSaveReqVO req = request(); change.accept(req);
            assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () -> controller.createAndStartSealApplyBill(req));
        }
        verifyNoInteractions(mapper, bpm, start);
    }
    @Test void forgedStarterOrOrganizationRejected() {
        SealApplyBillSaveReqVO starter = request(); starter.setCreator("999");
        assertThrows(AccessDeniedException.class, () -> controller.createAndStartSealApplyBill(starter));
        SealApplyBillSaveReqVO company = request(); company.setCompanyId(999L);
        assertThrows(AccessDeniedException.class, () -> controller.createAndStartSealApplyBill(company));
        SealApplyBillSaveReqVO dept = request(); dept.setDeptId(999L);
        assertThrows(AccessDeniedException.class, () -> controller.createAndStartSealApplyBill(dept));
        verifyNoInteractions(mapper, bpm);
    }
    @Test void existingAttachmentIdsRejected() {
        SealApplyBillSaveReqVO req = request();
        req.setAttachments(List.of(new AttachmentSaveReqVO().setId(10L)));
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () -> controller.createAndStartSealApplyBill(req));
        req.setAttachments(List.of(new AttachmentSaveReqVO().setBusinessId(999L)));
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () -> controller.createAndStartSealApplyBill(req));
        verifyNoInteractions(mapper, bpm, start);
    }
    @Test void eligibilityFailureDoesNotWrite() {
        when(start.validateStart(anyString())).thenThrow(new AccessDeniedException("Hidden"));
        assertThrows(AccessDeniedException.class, () -> controller.createAndStartSealApplyBill(request()));
        verifyNoInteractions(mapper, bpm);
    }
    public interface FilteredDeptMapper {
        @Select("SELECT id,name,parent_id,org_type FROM system_dept_probe WHERE id = #{id}")
        DeptRespDTO get(Long id);
    }

    private FilteredDeptMapper installRealDeptFiltering(boolean self) throws Exception {
        jdbc.execute("CREATE TABLE IF NOT EXISTS system_dept_probe(id BIGINT PRIMARY KEY, name VARCHAR, parent_id BIGINT, org_type VARCHAR, tenant_id BIGINT)");
        jdbc.update("DELETE FROM system_dept_probe");
        jdbc.update("INSERT INTO system_dept_probe VALUES (20,'Current dept',10,'2',1),(10,'Current company',0,'1',1),(30,'Other tenant company',0,'1',2)");
        PermissionCommonApi permissions = mock(PermissionCommonApi.class);
        when(permissions.getDeptDataPermission(42L)).thenReturn(CommonResult.success(
                new DeptDataPermissionRespDTO().setAll(false).setSelf(self)
                        .setDeptIds(self ? Set.of() : Set.of(20L))));
        DeptDataPermissionRule rule = new DeptDataPermissionRule(permissions);
        rule.addDeptColumn("system_dept_probe", "id");
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override public net.sf.jsqlparser.expression.Expression getTenantId() {
                return new net.sf.jsqlparser.expression.LongValue(TenantContextHolder.getRequiredTenantId());
            }
        }));
        interceptor.addInnerInterceptor(new DataPermissionInterceptor(new DataPermissionRuleHandler(
                new DataPermissionRuleFactoryImpl(List.of(rule)))));
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setCacheEnabled(false);
        configuration.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);
        configuration.addMapper(FilteredDeptMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(context.getBean(DriverManagerDataSource.class));
        factory.setConfiguration(configuration);
        factory.setPlugins(interceptor);
        FilteredDeptMapper filtered = new SqlSessionTemplate(factory.getObject()).getMapper(FilteredDeptMapper.class);
        // Only API transport is stubbed: responses come from actual MyBatis + tenant/data permission SQL.
        doAnswer(call -> CommonResult.success(filtered.get(call.getArgument(0))))
                .when(context.getBean(DeptApi.class)).getDept(anyLong());
        return filtered;
    }

    @Test void departmentOnlyCanDeriveParentCompanyWithoutBroadeningBusinessScope() throws Exception {
        assertRestrictedOrganizationStart(false);
    }
    @Test void selfOnlyCanDeriveOwnDepartmentWithoutBroadeningBusinessScope() throws Exception {
        assertRestrictedOrganizationStart(true);
    }
    private void assertRestrictedOrganizationStart(boolean self) throws Exception {
        FilteredDeptMapper filtered = installRealDeptFiltering(self);
        assertNull(filtered.get(10L)); // Before fix: parent company is genuinely hidden by SQL.
        assertEquals(self, filtered.get(20L) == null);
        when(bpm.createProcessInstance(eq(42L), any())).thenAnswer(call -> {
            assertNull(DataPermissionContextHolder.get());
            assertEquals(1L, TenantContextHolder.getTenantId());
            assertNull(filtered.get(10L)); // Ignore has ended before BPM/other business reads.
            return CommonResult.success("new-instance");
        });
        assertEquals(100L, controller.createAndStartSealApplyBill(request()).getCheckedData());
        assertNull(DataPermissionContextHolder.get());
        assertNull(filtered.get(10L));
        // Even inside the narrowly ignored derivation, a different tenant cannot supply the company.
        jdbc.update("UPDATE system_dept_probe SET parent_id=30 WHERE id=20");
        SealApplyBillSaveReqVO foreignCompany = request();
        foreignCompany.setCompanyId(30L);
        assertThrows(AccessDeniedException.class, () -> controller.createAndStartSealApplyBill(foreignCompany));
        assertNull(DataPermissionContextHolder.get());
        assertEquals(1L, TenantContextHolder.getTenantId());
        verify(bpm, times(1)).createProcessInstance(anyLong(), any());
    }

    @Test void uiAttachmentPayloadCreatesOnlyNewOwnership() {
        SealApplyBillSaveReqVO req = request();
        AttachmentSaveReqVO attachment = new AttachmentSaveReqVO();
        attachment.setFileUrl("https://example.test/file.pdf");
        attachment.setFilePath("https://example.test/file.pdf");
        attachment.setFileName("file.pdf");
        attachment.setFileSize(0L);
        req.setAttachments(List.of(attachment)); // Same whitelist as new/copy form: no source id or business owner.
        assertEquals(100L, controller.createAndStartSealApplyBill(req).getCheckedData());
        verify(context.getBean(AttachmentService.class)).saveAttachmentList(eq("103"), eq(100L), eq(req.getAttachments()));
        assertNull(attachment.getId());
        verify(mapper, never()).insertOrUpdate(any(SealApplyBillDO.class));
    }

    @Test void successfulRealFlowableStartRollsBackWhenLocalWriteBackFails() {
        org.flowable.spring.SpringProcessEngineConfiguration configuration =
                new org.flowable.spring.SpringProcessEngineConfiguration();
        configuration.setDataSource(context.getBean(DriverManagerDataSource.class));
        configuration.setTransactionManager(context.getBean(PlatformTransactionManager.class));
        configuration.setDatabaseSchemaUpdate("true");
        configuration.setAsyncExecutorActivate(false);
        org.flowable.engine.ProcessEngine engine = configuration.buildProcessEngine();
        try {
            engine.getRepositoryService().createDeployment().addString("seal.bpmn20.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="test">
                      <process id="oa_seal_apply_bill" isExecutable="true">
                        <startEvent id="start"/><sequenceFlow id="f1" sourceRef="start" targetRef="approve"/>
                        <userTask id="approve" name="Approve"/><sequenceFlow id="f2" sourceRef="approve" targetRef="end"/>
                        <endEvent id="end"/>
                      </process>
                    </definitions>
                    """).deploy();
            when(bpm.createProcessInstance(eq(42L), any())).thenAnswer(invocation -> {
                BpmProcessInstanceCreateReqDTO dto = invocation.getArgument(1);
                String id = engine.getRuntimeService().startProcessInstanceByKey(
                        dto.getProcessDefinitionKey(), dto.getBusinessKey(), dto.getVariables()).getId();
                assertEquals(1, engine.getRuntimeService().createProcessInstanceQuery().count());
                return CommonResult.success(id);
            });
            when(mapper.updateById(any(SealApplyBillDO.class)))
                    .thenThrow(new IllegalStateException("local write-back failed after successful BPM"));
            assertThrows(IllegalStateException.class, () -> controller.createAndStartSealApplyBill(request()));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM seal_tx_probe", Integer.class));
            assertEquals(0, engine.getRuntimeService().createProcessInstanceQuery().count());
            assertEquals(0, engine.getTaskService().createTaskQuery().count());
            assertEquals(0, engine.getHistoryService().createHistoricProcessInstanceQuery().count());
        } finally {
            engine.close();
        }
    }

    @Test void bpmFailureRollsBackActualLocalDatabaseThroughSpringProxy() {
        when(bpm.createProcessInstance(eq(42L), any())).thenThrow(new IllegalStateException("BPM failed"));
        assertThrows(IllegalStateException.class, () -> controller.createAndStartSealApplyBill(request()));
        verify(mapper).insert(any(SealApplyBillDO.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM seal_tx_probe", Integer.class));
    }
}
