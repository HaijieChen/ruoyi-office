package cn.iocoder.yudao.module.bpm.service.oa;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOAOvertimeCreateReqVO;
import cn.iocoder.yudao.module.bpm.dal.mysql.oa.BpmOAOvertimeMapper;
import cn.iocoder.yudao.module.bpm.dal.mysql.oa.BpmOAQuotaLockMapper;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants;
import cn.iocoder.yudao.module.bpm.framework.security.OaBillAccessPermission;
import cn.iocoder.yudao.module.bpm.service.message.BpmMessageService;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceServiceImpl;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.Resource;
import javax.sql.DataSource;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Real overtime Mapper + TX on oa_u12_iso. Independent JDBC reads.
 * Flowable engine APIs mocked; service/Mapper/transaction are real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = OaAttendancePathIT.App.class)
@ActiveProfiles("oa-u12-iso")
class OaAttendancePathIT {

    private static final long TENANT = 1L;
    private static final int RUNNING = BpmTaskStatusEnum.RUNNING.getStatus();
    private static final int APPROVE = BpmTaskStatusEnum.APPROVE.getStatus();
    private static final LocalDateTime DAY = LocalDateTime.of(2026, 9, 6, 10, 0, 0);

    @Resource
    private BpmOAOvertimeServiceImpl overtimeService;
    @Resource
    private BpmProcessInstanceApi processInstanceApi;
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private DataSource dataSource;
    @Resource
    private PlatformTransactionManager transactionManager;

    @BeforeAll
    static void requireIso() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 13306), 2000);
        } catch (Exception ex) {
            assumeTrue(false, "isolated mysql 127.0.0.1:13306 not reachable");
        }
    }

    @BeforeEach
    void resetDb() {
        String url = jdbcUrl();
        assertTrue(url.contains("13306"));
        assertTrue(url.contains("oa_u12_iso"));
        assertFalse(url.contains("oa_u9_iso"));
        assertFalse(url.contains("oa_u10_iso"));
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS bpm_oa_overtime (
                    id bigint NOT NULL AUTO_INCREMENT,
                    user_id bigint NOT NULL,
                    reason varchar(500) NOT NULL,
                    start_time datetime NOT NULL,
                    end_time datetime NOT NULL,
                    hours decimal(8,1) NOT NULL,
                    holiday varchar(16) NOT NULL,
                    attachment_urls json DEFAULT NULL,
                    status tinyint NOT NULL,
                    process_instance_id varchar(64) DEFAULT NULL,
                    attendance_sync_status varchar(32) NOT NULL DEFAULT 'NOT_SYNCED',
                    creator varchar(64) DEFAULT '',
                    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updater varchar(64) DEFAULT '',
                    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    deleted bit(1) NOT NULL DEFAULT b'0',
                    tenant_id bigint NOT NULL DEFAULT 0,
                    PRIMARY KEY (id)
                ) ENGINE=InnoDB
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS bpm_oa_quota_lock (
                    tenant_id bigint NOT NULL DEFAULT 0,
                    user_id bigint NOT NULL,
                    quota_type varchar(32) NOT NULL,
                    period varchar(16) NOT NULL,
                    creator varchar(64) DEFAULT '',
                    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updater varchar(64) DEFAULT '',
                    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    PRIMARY KEY (tenant_id, user_id, quota_type, period)
                ) ENGINE=InnoDB
                """);
        jdbcTemplate.execute("TRUNCATE TABLE bpm_oa_overtime");
        jdbcTemplate.execute("TRUNCATE TABLE bpm_oa_quota_lock");
        TenantContextHolder.setTenantId(TENANT);
        when(processInstanceApi.createProcessInstance(any(), any(BpmProcessInstanceCreateReqDTO.class)))
                .thenReturn(CommonResult.success("pi-r1"));
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void createOvertime_immediateApproveReconcile_independentConnectionSeesApprove() {
        stubEngineStatus("pi-r1", BpmProcessInstanceStatusEnum.APPROVE.getStatus());
        Long id = overtimeService.createOvertime(88001L, overtimeReq());
        assertEquals(Integer.valueOf(APPROVE), independentStatus(id));
        assertEquals("pi-r1", independentPiId(id));
    }

    @Test
    void processCompleted_afterCommit_updatesBoundRowOnIndependentConnection() {
        jdbcTemplate.update("""
                INSERT INTO bpm_oa_overtime
                (user_id, reason, start_time, end_time, hours, holiday, status, process_instance_id, tenant_id, deleted)
                VALUES (88002, 'r', ?, ?, 2.0, 'false', ?, 'pi-c1', ?, b'0')
                """, DAY, DAY.plusHours(2), RUNNING, TENANT);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM bpm_oa_overtime WHERE process_instance_id='pi-c1'", Long.class);
        BpmProcessInstanceServiceImpl piService = newCompletedService();
        ProcessInstance instance = mockCompletedInstance("pi-c1", String.valueOf(id), "1");
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                piService.processProcessInstanceCompleted(instance));
        assertEquals(Integer.valueOf(APPROVE), independentStatus(id));
    }

    @Test
    void processCompleted_rollback_doesNotUpdateIndependentConnection() {
        jdbcTemplate.update("""
                INSERT INTO bpm_oa_overtime
                (user_id, reason, start_time, end_time, hours, holiday, status, process_instance_id, tenant_id, deleted)
                VALUES (88003, 'r', ?, ?, 2.0, 'false', ?, 'pi-c2', ?, b'0')
                """, DAY, DAY.plusHours(2), RUNNING, TENANT);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM bpm_oa_overtime WHERE process_instance_id='pi-c2'", Long.class);
        BpmProcessInstanceServiceImpl piService = newCompletedService();
        ProcessInstance instance = mockCompletedInstance("pi-c2", String.valueOf(id), "1");
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                piService.processProcessInstanceCompleted(instance);
                throw new RuntimeException("approval-rollback");
            });
        } catch (RuntimeException ex) {
            assertEquals("approval-rollback", ex.getMessage());
        }
        assertEquals(Integer.valueOf(RUNNING), independentStatus(id));
    }

    @Test
    void processCompleted_wrongPiId_doesNotUpdateIndependentConnection() {
        jdbcTemplate.update("""
                INSERT INTO bpm_oa_overtime
                (user_id, reason, start_time, end_time, hours, holiday, status, process_instance_id, tenant_id, deleted)
                VALUES (88004, 'r', ?, ?, 2.0, 'false', ?, 'pi-A', ?, b'0')
                """, DAY, DAY.plusHours(2), RUNNING, TENANT);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM bpm_oa_overtime WHERE process_instance_id='pi-A'", Long.class);
        BpmProcessInstanceServiceImpl piService = newCompletedService();
        ProcessInstance instance = mockCompletedInstance("pi-B", String.valueOf(id), "1");
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                piService.processProcessInstanceCompleted(instance));
        assertEquals(Integer.valueOf(RUNNING), independentStatus(id));
    }

    private void stubEngineStatus(String piId, int status) {
        RuntimeService runtime = mock(RuntimeService.class);
        ProcessInstanceQuery query = mock(ProcessInstanceQuery.class);
        ProcessInstance running = mock(ProcessInstance.class);
        when(runtime.createProcessInstanceQuery()).thenReturn(query);
        when(query.processInstanceId(piId)).thenReturn(query);
        when(query.includeProcessVariables()).thenReturn(query);
        when(query.singleResult()).thenReturn(running);
        when(running.getProcessVariables()).thenReturn(Map.of(
                BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS, status));
        @SuppressWarnings("unchecked")
        ObjectProvider<RuntimeService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(runtime);
        ReflectionTestUtils.setField(overtimeService, "runtimeServiceProvider", provider);
        @SuppressWarnings("unchecked")
        ObjectProvider<HistoryService> historyProvider = mock(ObjectProvider.class);
        when(historyProvider.getIfAvailable()).thenReturn(null);
        ReflectionTestUtils.setField(overtimeService, "historyServiceProvider", historyProvider);
    }

    private BpmProcessInstanceServiceImpl newCompletedService() {
        BpmProcessInstanceServiceImpl piService = new BpmProcessInstanceServiceImpl();
        RuntimeService runtime = mock(RuntimeService.class);
        ReflectionTestUtils.setField(piService, "runtimeService", runtime);
        ReflectionTestUtils.setField(piService, "messageService", mock(BpmMessageService.class));
        ReflectionTestUtils.setField(piService, "transactionManager", transactionManager);
        ReflectionTestUtils.setField(piService, "oaOvertimeService", overtimeService);
        ReflectionTestUtils.setField(piService, "oaPunchCorrectionService", mock(BpmOAPunchCorrectionService.class));
        cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService definitionService =
                mock(cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService.class);
        when(definitionService.getProcessDefinitionInfo(any()))
                .thenReturn(new cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO());
        ReflectionTestUtils.setField(piService, "processDefinitionService", definitionService);
        return piService;
    }

    private static ProcessInstance mockCompletedInstance(String piId, String businessKey, String tenant) {
        ProcessInstance instance = mock(ProcessInstance.class);
        when(instance.getProcessVariables()).thenReturn(Map.of(
                BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS,
                BpmProcessInstanceStatusEnum.RUNNING.getStatus()));
        when(instance.getProcessDefinitionKey()).thenReturn(BpmOAOvertimeServiceImpl.PROCESS_KEY);
        when(instance.getId()).thenReturn(piId);
        when(instance.getBusinessKey()).thenReturn(businessKey);
        when(instance.getTenantId()).thenReturn(tenant);
        when(instance.getSuperExecutionId()).thenReturn(null);
        when(instance.getProcessDefinitionId()).thenReturn("def-oa_overtime");
        return instance;
    }

    private Integer independentStatus(Long id) {
        DriverManagerDataSource independent = new DriverManagerDataSource();
        independent.setUrl(jdbcUrl());
        independent.setUsername("root");
        return new JdbcTemplate(independent).queryForObject(
                "SELECT status FROM bpm_oa_overtime WHERE id=?", Integer.class, id);
    }

    private String independentPiId(Long id) {
        DriverManagerDataSource independent = new DriverManagerDataSource();
        independent.setUrl(jdbcUrl());
        independent.setUsername("root");
        return new JdbcTemplate(independent).queryForObject(
                "SELECT process_instance_id FROM bpm_oa_overtime WHERE id=?", String.class, id);
    }

    private String jdbcUrl() {
        if (dataSource instanceof com.zaxxer.hikari.HikariDataSource hikari) {
            return hikari.getJdbcUrl();
        }
        return String.valueOf(jdbcTemplate.getDataSource());
    }

    private static BpmOAOvertimeCreateReqVO overtimeReq() {
        BpmOAOvertimeCreateReqVO req = new BpmOAOvertimeCreateReqVO();
        req.setReason("项目上线");
        req.setStartTime(DAY);
        req.setEndTime(DAY.plusHours(2));
        req.setHoliday("false");
        return req;
    }

    @SpringBootConfiguration
    @EnableTransactionManagement(proxyTargetClass = true)
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            MybatisPlusAutoConfiguration.class
    })
    @Import({YudaoMybatisAutoConfiguration.class, SpringUtil.class})
    static class App {
        @Bean
        BpmProcessInstanceApi processInstanceApi() {
            return mock(BpmProcessInstanceApi.class);
        }

        @Bean
        SecurityFrameworkService securityFrameworkService() {
            return mock(SecurityFrameworkService.class);
        }

        @Bean
        OaBillAccessPermission oaBillAccessPermission() {
            return mock(OaBillAccessPermission.class);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        BpmOAOvertimeServiceImpl overtimeService(BpmOAOvertimeMapper overtimeMapper,
                                                 BpmOAQuotaLockMapper quotaLockMapper,
                                                 BpmProcessInstanceApi processInstanceApi,
                                                 SecurityFrameworkService securityFrameworkService,
                                                 OaBillAccessPermission oaBillAccessPermission) {
            BpmOAOvertimeServiceImpl service = new BpmOAOvertimeServiceImpl();
            ReflectionTestUtils.setField(service, "overtimeMapper", overtimeMapper);
            ReflectionTestUtils.setField(service, "quotaLockMapper", quotaLockMapper);
            ReflectionTestUtils.setField(service, "processInstanceApi", processInstanceApi);
            ReflectionTestUtils.setField(service, "securityFrameworkService", securityFrameworkService);
            ReflectionTestUtils.setField(service, "oaBillAccessPermission", oaBillAccessPermission);
            return service;
        }
    }
}
