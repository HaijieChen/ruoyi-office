package cn.iocoder.yudao.module.oa.service.seal;

import cn.iocoder.yudao.common.server.attachment.service.AttachmentService;
import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.datapermission.core.db.DataPermissionRuleHandler;
import cn.iocoder.yudao.framework.datapermission.core.rule.DataPermissionRuleFactoryImpl;
import cn.iocoder.yudao.framework.datapermission.core.rule.dept.DeptDataPermissionRule;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessParticipantApi;
import cn.iocoder.yudao.module.oa.framework.security.OaProcessBillReadSupport;
import cn.iocoder.yudao.module.oa.dal.dataobject.seal.SealApplyBillDO;
import cn.iocoder.yudao.module.oa.dal.mysql.seal.SealApplyBillMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static cn.iocoder.yudao.module.oa.enums.ErrorCodeConstants.SEAL_APPLY_BILL_ACCESS_DENIED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * OA 接缝：真实 SQL/租户/SELF。参与判定走本测试内与引擎一致的查询，不是产品
 * {@code BpmProcessParticipantApiImpl}（产品 Flowable 链在 bpm-server
 * {@code BpmProcessParticipantApiFlowableTest}，避免 oa-server 依赖 bpm-server）。
 */
class SealApplyBillDetailDataScopeTest {

    private SqlSession session;
    private ProcessEngine engine;
    private SealApplyBillServiceImpl service;
    private SecurityFrameworkService security;

    @BeforeEach
    void setup() throws Exception {
        TenantContextHolder.setTenantId(1L);
        login(681);
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:seal" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        MybatisConfiguration cfg = new MybatisConfiguration();
        cfg.setMapUnderscoreToCamelCase(true);
        cfg.setCacheEnabled(false);
        cfg.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);
        cfg.setEnvironment(new Environment("test", new JdbcTransactionFactory(), ds));
        GlobalConfig global = new GlobalConfig();
        GlobalConfig.DbConfig db = new GlobalConfig.DbConfig();
        db.setLogicDeleteField("deleted");
        db.setLogicDeleteValue("true");
        db.setLogicNotDeleteValue("false");
        global.setDbConfig(db);
        GlobalConfigUtils.setGlobalConfig(cfg, global);
        PermissionCommonApi permissions = mock(PermissionCommonApi.class);
        when(permissions.getDeptDataPermission(anyLong())).thenReturn(CommonResult.success(
                new DeptDataPermissionRespDTO().setAll(false).setSelf(true).setDeptIds(Set.of())));
        DeptDataPermissionRule rule = new DeptDataPermissionRule(permissions);
        rule.addUserColumn("oa_seal_apply_bill", "creator");
        TenantProperties props = new TenantProperties();
        props.setIgnoreTables(Set.of());
        MybatisPlusInterceptor plugins = new MybatisPlusInterceptor();
        plugins.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(props)));
        plugins.addInnerInterceptor(new DataPermissionInterceptor(
                new DataPermissionRuleHandler(new DataPermissionRuleFactoryImpl(List.of(rule)))));
        cfg.addInterceptor(plugins);
        cfg.addMapper(SealApplyBillMapper.class);
        session = new MybatisSqlSessionFactoryBuilder().build(cfg).openSession(true);
        var table = TableInfoHelper.getTableInfo(SealApplyBillDO.class);
        List<String> columns = new ArrayList<>();
        columns.add("id BIGINT PRIMARY KEY");
        for (var field : table.getFieldList()) {
            String sqlType = "VARCHAR(4000)";
            if ("deleted".equals(field.getProperty())) {
                sqlType = "BOOLEAN DEFAULT FALSE";
            } else if (Number.class.isAssignableFrom(field.getPropertyType())) {
                sqlType = "DECIMAL(22,4)";
            }
            columns.add(field.getColumn() + " " + sqlType);
        }
        if (table.getFieldList().stream().noneMatch(f -> "tenant_id".equals(f.getColumn()))) {
            columns.add("tenant_id BIGINT");
        }
        session.getConnection().createStatement().execute(
                "CREATE TABLE " + table.getTableName() + "(" + String.join(",", columns) + ")");
        session.getConnection().createStatement().execute(
                "INSERT INTO oa_seal_apply_bill(id,tenant_id,creator,process_instance_id,deleted) VALUES (526,1,'838','placeholder',false),(527,2,'838','placeholder',false)");
        engine = ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration()
                .setJdbcUrl("jdbc:h2:mem:sealflow" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1")
                .setDatabaseSchemaUpdate("true").setAsyncExecutorActivate(false).buildProcessEngine();
        engine.getRepositoryService().createDeployment().addString("seal.bpmn20.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="test">
                <process id="seal" isExecutable="true"><startEvent id="start"/><sequenceFlow id="a" sourceRef="start" targetRef="past"/>
                <userTask id="past" flowable:assignee="683"/><sequenceFlow id="b" sourceRef="past" targetRef="current"/>
                <userTask id="current" flowable:assignee="681"/><sequenceFlow id="c" sourceRef="current" targetRef="end"/><endEvent id="end"/></process></definitions>
                """).deploy();
        String pi = engine.getRuntimeService().startProcessInstanceByKey("seal").getId();
        engine.getTaskService().complete(engine.getTaskService().createTaskQuery().processInstanceId(pi).singleResult().getId());
        session.getConnection().createStatement().execute(
                "UPDATE oa_seal_apply_bill SET process_instance_id='" + pi + "'");
        BpmProcessParticipantApi participantApi = processInstanceId -> {
            Long uid = SecurityFrameworkUtils.getLoginUserId();
            if (uid == null || processInstanceId == null || processInstanceId.isBlank()) {
                return CommonResult.success(false);
            }
            String user = String.valueOf(uid);
            long active = engine.getTaskService().createTaskQuery()
                    .processInstanceId(processInstanceId).taskCandidateOrAssigned(user).count();
            long historic = engine.getHistoryService().createHistoricTaskInstanceQuery()
                    .processInstanceId(processInstanceId).taskAssignee(user).count();
            return CommonResult.success(active > 0 || historic > 0);
        };
        security = mock(SecurityFrameworkService.class);
        when(security.hasPermission("oa:seal-apply-bill:query")).thenReturn(false);
        service = new SealApplyBillServiceImpl();
        ReflectionTestUtils.setField(service, "sealApplyBillMapper", session.getMapper(SealApplyBillMapper.class));
        OaProcessBillReadSupport support = new OaProcessBillReadSupport();
        ReflectionTestUtils.setField(support, "processParticipantApi", participantApi);
        ReflectionTestUtils.setField(support, "securityFrameworkService", security);
        ReflectionTestUtils.setField(service, "processBillReadSupport", support);
        AttachmentService attachments = mock(AttachmentService.class);
        when(attachments.getAttachmentListByBusiness(org.mockito.ArgumentMatchers.anyString(), anyLong()))
                .thenReturn(List.of());
        ReflectionTestUtils.setField(service, "attachmentService", attachments);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
        if (session != null) {
            session.close();
        }
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    void currentAndHistoricAssigneesReadOutsideSelfList() {
        login(681);
        assertEquals(526L, service.getSealApplyBillInfo(526L).getId());
        login(683);
        assertEquals(526L, service.getSealApplyBillInfo(526L).getId());
        login(838);
        assertEquals(526L, service.getSealApplyBillInfo(526L).getId());
        login(681);
        assertEquals(0, session.getMapper(SealApplyBillMapper.class).selectList(null).size());
    }

    @Test
    void queryPermissionDoesNotReadOthersWhenSelfAndNotParticipant() {
        login(999);
        when(security.hasPermission("oa:seal-apply-bill:query")).thenReturn(true);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.getSealApplyBillInfo(526L));
        assertEquals(SEAL_APPLY_BILL_ACCESS_DENIED.getCode(), ex.getCode());
    }

    @Test
    void unrelatedPersonDeniedAndCrossTenantHidden() {
        login(999);
        assertEquals(SEAL_APPLY_BILL_ACCESS_DENIED.getCode(),
                assertThrows(ServiceException.class, () -> service.getSealApplyBillInfo(526L)).getCode());
        login(681);
        assertEquals(cn.iocoder.yudao.module.oa.enums.ErrorCodeConstants.SEAL_APPLY_BILL_NOT_EXISTS.getCode(),
                assertThrows(ServiceException.class, () -> service.getSealApplyBillInfo(527L)).getCode());
    }

    private void login(long uid) {
        LoginUser user = new LoginUser().setId(uid).setUserType(2).setTenantId(1L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        SecurityFrameworkUtils.setLoginUser(user, new org.springframework.mock.web.MockHttpServletRequest());
    }
}
