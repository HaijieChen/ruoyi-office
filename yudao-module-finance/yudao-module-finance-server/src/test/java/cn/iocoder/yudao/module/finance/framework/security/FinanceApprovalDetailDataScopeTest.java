package cn.iocoder.yudao.module.finance.framework.security;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.*;
import cn.iocoder.yudao.framework.datapermission.core.aop.DataPermissionAnnotationAdvisor;
import cn.iocoder.yudao.framework.datapermission.core.db.DataPermissionRuleHandler;
import cn.iocoder.yudao.framework.datapermission.core.rule.DataPermissionRuleFactoryImpl;
import cn.iocoder.yudao.framework.datapermission.core.rule.dept.DeptDataPermissionRule;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.finance.dal.mysql.contract.FinanceContractApplicationMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.payment.FinancePaymentApplicationMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.invoice.FinanceInvoiceApplicationMapper;
import cn.iocoder.yudao.module.finance.dal.dataobject.contract.FinanceContractApplicationDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentApplicationDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationDO;
import cn.iocoder.yudao.module.finance.service.contract.FinanceContractApplicationServiceImpl;
import cn.iocoder.yudao.module.finance.service.payment.FinancePaymentApplicationServiceImpl;
import com.baomidou.mybatisplus.core.*;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.*;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.flowable.engine.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real SQL, production data/tenant interceptors and Spring advisor; only unrelated service dependencies are mocked. */
class FinanceApprovalDetailDataScopeTest {
    private SqlSession session;
    private FinanceContractApplicationServiceImpl contract;
    private FinancePaymentApplicationServiceImpl payment;
    private FinanceInvoiceAccessPermission invoice;
    private ProcessEngine engine;

    @BeforeEach void setup() throws Exception {
        TenantContextHolder.setTenantId(1L);
        login(681);
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:detail" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        MybatisConfiguration cfg = new MybatisConfiguration();
        cfg.setMapUnderscoreToCamelCase(true);
        cfg.setCacheEnabled(false);
        cfg.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);
        cfg.setEnvironment(new Environment("test", new JdbcTransactionFactory(), ds));
        GlobalConfig global = new GlobalConfig();
        GlobalConfig.DbConfig db = new GlobalConfig.DbConfig();
        db.setLogicDeleteField("deleted"); db.setLogicDeleteValue("true"); db.setLogicNotDeleteValue("false");
        global.setDbConfig(db); GlobalConfigUtils.setGlobalConfig(cfg, global);
        PermissionCommonApi permissions = mock(PermissionCommonApi.class);
        when(permissions.getDeptDataPermission(anyLong())).thenReturn(CommonResult.success(
            new DeptDataPermissionRespDTO().setAll(false).setSelf(true).setDeptIds(Set.of())));
        DeptDataPermissionRule rule = new DeptDataPermissionRule(permissions);
        for (String table : List.of("finance_contract_application", "finance_payment_application", "finance_invoice_application")) {
            rule.addUserColumn(table, "applicant_user_id");
        }
        TenantProperties props = new TenantProperties(); props.setIgnoreTables(Set.of());
        MybatisPlusInterceptor plugins = new MybatisPlusInterceptor();
        plugins.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(props)));
        plugins.addInnerInterceptor(new DataPermissionInterceptor(new DataPermissionRuleHandler(new DataPermissionRuleFactoryImpl(List.of(rule)))));
        cfg.addInterceptor(plugins);
        cfg.addMapper(FinanceContractApplicationMapper.class);
        cfg.addMapper(FinancePaymentApplicationMapper.class);
        cfg.addMapper(FinanceInvoiceApplicationMapper.class);
        session = new MybatisSqlSessionFactoryBuilder().build(cfg).openSession(true);
        for (Class<?> type : List.of(FinanceContractApplicationDO.class, FinancePaymentApplicationDO.class, FinanceInvoiceApplicationDO.class)) {
            var table = TableInfoHelper.getTableInfo(type);
            List<String> columns = new ArrayList<>(); columns.add("id BIGINT PRIMARY KEY");
            for (var field : table.getFieldList()) {
                String sqlType = field.getProperty().equals("deleted") ? "BOOLEAN DEFAULT FALSE" : "VARCHAR(4000)";
                if (Number.class.isAssignableFrom(field.getPropertyType())) sqlType = "DECIMAL(22,4)";
                columns.add(field.getColumn() + " " + sqlType);
            }
            if (table.getFieldList().stream().noneMatch(f -> f.getColumn().equals("tenant_id"))) columns.add("tenant_id BIGINT");
            session.getConnection().createStatement().execute("CREATE TABLE " + table.getTableName() + "(" + String.join(",",columns) + ")");
            session.getConnection().createStatement().execute("INSERT INTO " + table.getTableName() + "(id,tenant_id,applicant_user_id,process_instance_id) VALUES (526,1,838,'placeholder'),(527,2,838,'placeholder')");
        }
        engine = ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration()
            .setJdbcUrl("jdbc:h2:mem:flow" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1")
            .setDatabaseSchemaUpdate("true").setAsyncExecutorActivate(false).buildProcessEngine();
        engine.getRepositoryService().createDeployment().addString("detail.bpmn20.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="test">
            <process id="detail" isExecutable="true"><startEvent id="start"/><sequenceFlow id="a" sourceRef="start" targetRef="past"/>
            <userTask id="past" flowable:assignee="683"/><sequenceFlow id="b" sourceRef="past" targetRef="current"/>
            <userTask id="current" flowable:assignee="681"/><sequenceFlow id="c" sourceRef="current" targetRef="end"/><endEvent id="end"/></process></definitions>
            """).deploy();
        String pi = engine.getRuntimeService().startProcessInstanceByKey("detail").getId();
        engine.getTaskService().complete(engine.getTaskService().createTaskQuery().processInstanceId(pi).singleResult().getId());
        for (String table : List.of("finance_contract_application", "finance_payment_application", "finance_invoice_application")) {
            session.getConnection().createStatement().execute("UPDATE " + table + " SET process_instance_id='" + pi + "'");
        }
        FinanceProcessParticipantSupport support = new FinanceProcessParticipantSupport();
        ReflectionTestUtils.setField(support,"taskServiceProvider",provider(engine.getTaskService()));
        ReflectionTestUtils.setField(support,"historyServiceProvider",provider(engine.getHistoryService()));
        var c = construct(FinanceContractApplicationServiceImpl.class, session.getMapper(FinanceContractApplicationMapper.class));
        ReflectionTestUtils.setField(c,"processParticipantSupport",support); contract = proxy(c);
        var p = construct(FinancePaymentApplicationServiceImpl.class, session.getMapper(FinancePaymentApplicationMapper.class));
        ReflectionTestUtils.setField(p,"processParticipantSupport",support); payment = proxy(p);
        var i = new FinanceInvoiceAccessPermission();
        ReflectionTestUtils.setField(i,"invoiceApplicationMapper",session.getMapper(FinanceInvoiceApplicationMapper.class));
        ReflectionTestUtils.setField(i,"processParticipantSupport",support); invoice = proxy(i);
    }
    private void login(long uid) {
        LoginUser user = new LoginUser().setId(uid).setUserType(2).setTenantId(1L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,List.of()));
    }
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> p = mock(ObjectProvider.class); when(p.getIfAvailable()).thenReturn(value); return p;
    }
    private static <T> T construct(Class<T> type, Object mapper) throws Exception {
        var ctor = type.getConstructors()[0]; Object[] args = Arrays.stream(ctor.getParameterTypes()).map(t -> t.isInstance(mapper) ? mapper : mock(t)).toArray();
        return type.cast(ctor.newInstance(args));
    }
    private static <T> T proxy(T target) {
        ProxyFactory f = new ProxyFactory(target); f.setProxyTargetClass(true); f.addAdvisor(new DataPermissionAnnotationAdvisor()); return (T) f.getProxy();
    }
    @AfterEach void cleanup() {
        SecurityContextHolder.clearContext(); TenantContextHolder.clear();
        if(session!=null) session.close(); if(engine!=null) engine.close();
    }
    @ParameterizedTest @ValueSource(longs={681,683,838})
    void relatedPeopleReadDetailsOutsideSelfList(long uid) {
        login(uid);
        assertEquals(526L,contract.getApplication(526L,uid,false).getId());
        assertTrue(contract.canAccessDetail(526L,uid));
        assertEquals(526L,payment.getApplicationForRead(526L,uid,false).getId());
        assertEquals(526L,payment.getOrdinaryApplicationForRead(526L,uid,false).getId());
        assertTrue(payment.canAccessDetail(526L,uid)); assertTrue(invoice.canTaskContextOrOwnerRead(526L));
        int expected = uid==838 ? 1 : 0;
        assertEquals(expected,session.getMapper(FinanceContractApplicationMapper.class).selectList(null).size());
        assertEquals(expected,session.getMapper(FinancePaymentApplicationMapper.class).selectList(null).size());
        assertEquals(expected,session.getMapper(FinanceInvoiceApplicationMapper.class).selectList(null).size());
    }
    @Test void unrelatedPersonIsStillDenied() {
        login(999);
        assertFalse(contract.canAccessDetail(526L,999L)); assertFalse(payment.canAccessDetail(526L,999L));
        assertFalse(invoice.canTaskContextOrOwnerRead(526L));
        assertEquals(CONTRACT_APPLICATION_ACCESS_DENIED.getCode(), assertThrows(ServiceException.class,()->contract.getApplication(526L,999L,false)).getCode());
        assertEquals(PAYMENT_APPLICATION_ACCESS_DENIED.getCode(), assertThrows(ServiceException.class,()->payment.getApplicationForRead(526L,999L,false)).getCode());
    }
    @Test void relatedPersonCannotCrossTenant() {
        assertFalse(contract.canAccessDetail(527L,681L)); assertFalse(payment.canAccessDetail(527L,681L));
        assertFalse(invoice.canTaskContextOrOwnerRead(527L));
        assertEquals(CONTRACT_APPLICATION_NOT_EXISTS.getCode(), assertThrows(ServiceException.class,()->contract.getApplication(527L,681L,false)).getCode());
        assertEquals(PAYMENT_APPLICATION_NOT_EXISTS.getCode(), assertThrows(ServiceException.class,()->payment.getApplicationForRead(527L,681L,false)).getCode());
    }
    @Test void candidateCanReadUnassignedTask() {
        var task=engine.getTaskService().createTaskQuery().taskDefinitionKey("current").singleResult();
        engine.getTaskService().setAssignee(task.getId(),null); engine.getTaskService().addCandidateUser(task.getId(),"682"); login(682);
        assertTrue(contract.canAccessDetail(526L,682L)); assertTrue(payment.canAccessDetail(526L,682L));
        assertTrue(invoice.canTaskContextOrOwnerRead(526L));
        assertEquals(526L,contract.getApplication(526L,682L,false).getId());
        assertEquals(526L,payment.getOrdinaryApplicationForRead(526L,682L,false).getId());
    }
}
