package cn.iocoder.yudao.module.finance.service.payment;

import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.finance.dal.dataobject.contract.FinanceContractApplicationDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.customer.FinanceCustomerCompanyDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentApplicationDO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PAY-R15：生产 {@link TenantDatabaseInterceptor} SQL 租户证据（非合成 allowlist handler）。
 * <p>注册真实 DO 的 MyBatis {@link TableInfoHelper} 元数据，走 ignoreTables + TenantBaseDO 判定。
 */
class FinancePaymentTenantLineSqlContractTest {

    @BeforeAll
    static void registerFinanceTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, FinancePaymentApplicationDO.class);
        TableInfoHelper.initTableInfo(assistant, FinanceCustomerCompanyDO.class);
        TableInfoHelper.initTableInfo(assistant, FinanceContractApplicationDO.class);
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void entitiesAreTenantBaseDo() {
        assertTrue(TenantBaseDO.class.isAssignableFrom(FinancePaymentApplicationDO.class));
        assertTrue(TenantBaseDO.class.isAssignableFrom(FinanceCustomerCompanyDO.class));
        assertTrue(TenantBaseDO.class.isAssignableFrom(FinanceContractApplicationDO.class));
    }

    @Test
    void prodInterceptor_selectPaymentGet_exactTenantId() {
        long tid = 11L;
        TenantContextHolder.setTenantId(tid);
        String sql = rewrite(prodInterceptor(),
                "SELECT * FROM finance_payment_application WHERE id = 1 AND deleted = 0");
        assertExactTenantPredicate(sql, tid);
    }

    @Test
    void prodInterceptor_sumPaid_exactTenantId() {
        long tid = 22L;
        TenantContextHolder.setTenantId(tid);
        String sql = rewrite(prodInterceptor(),
                "SELECT COALESCE(SUM(apply_amount), 0) FROM finance_payment_application "
                        + "WHERE payee_company_id = 9 AND status = 'PAID' AND deleted = 0");
        assertExactTenantPredicate(sql, tid);
    }

    @Test
    void prodInterceptor_customerCompanyReference_exactTenantId() {
        long tid = 33L;
        TenantContextHolder.setTenantId(tid);
        String sql = rewrite(prodInterceptor(),
                "SELECT * FROM finance_customer_company WHERE id = 9 AND deleted = 0");
        assertExactTenantPredicate(sql, tid);
    }

    @Test
    void prodInterceptor_contractApplicationReference_exactTenantId() {
        long tid = 44L;
        TenantContextHolder.setTenantId(tid);
        String sql = rewrite(prodInterceptor(),
                "SELECT * FROM finance_contract_application WHERE id = 7 AND deleted = 0");
        assertExactTenantPredicate(sql, tid);
    }

    /**
     * 跨租户隔离证据（SQL 层）：同一 get 在租户 A/B 下分别绑定精确 tenant_id，
     * 证明生产 interceptor 不会把 A 的上下文泄漏到 B 的查询。
     * <p>非 mock mapper 假隔离。
     */
    @Test
    void prodInterceptor_crossTenantGet_bindsDifferentExactTenantIds() {
        TenantLineInnerInterceptor interceptor = prodInterceptor();

        TenantContextHolder.setTenantId(100L);
        String sqlA = rewrite(interceptor,
                "SELECT * FROM finance_payment_application WHERE id = 999 AND deleted = 0");
        assertExactTenantPredicate(sqlA, 100L);
        assertFalse(matchesTenantEquals(sqlA, 200L), "tenant A SQL must not bind B: " + sqlA);

        TenantContextHolder.setTenantId(200L);
        String sqlB = rewrite(interceptor,
                "SELECT * FROM finance_payment_application WHERE id = 999 AND deleted = 0");
        assertExactTenantPredicate(sqlB, 200L);
        assertFalse(matchesTenantEquals(sqlB, 100L), "tenant B SQL must not bind A: " + sqlB);
    }

    @Test
    void prodInterceptor_unregisteredTable_ignored() {
        TenantContextHolder.setTenantId(1L);
        String sql = rewrite(prodInterceptor(), "SELECT * FROM some_unknown_table WHERE id = 1");
        // 无 TableInfo → computeIgnoreTable=true → 不改写 tenant
        assertFalse(matchesTenantEquals(sql, 1L), "unknown table should not gain tenant filter: " + sql);
    }

    @Test
    void prodInterceptor_ignoreTablesConfig_skipsPayment() {
        TenantContextHolder.setTenantId(55L);
        TenantProperties props = new TenantProperties();
        Set<String> ignore = new HashSet<>();
        ignore.add("finance_payment_application");
        props.setIgnoreTables(ignore);
        TenantLineInnerInterceptor interceptor =
                new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(props));
        String sql = rewrite(interceptor,
                "SELECT * FROM finance_payment_application WHERE id = 1 AND deleted = 0");
        assertFalse(matchesTenantEquals(sql, 55L),
                "ignoreTables should skip rewrite: " + sql);
    }

    private static TenantLineInnerInterceptor prodInterceptor() {
        TenantProperties props = new TenantProperties();
        props.setIgnoreTables(Collections.emptySet());
        return new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(props));
    }

    private static String rewrite(TenantLineInnerInterceptor interceptor, String sql) {
        String out = interceptor.parserSingle(sql, null);
        assertNotNull(out);
        return out;
    }

    private static void assertExactTenantPredicate(String sql, long tenantId) {
        assertTrue(sql.toLowerCase(Locale.ROOT).contains("tenant_id"), "missing tenant_id: " + sql);
        assertTrue(matchesTenantEquals(sql, tenantId),
                "expected exact tenant_id = " + tenantId + " in: " + sql);
    }

    /** 匹配 tenant_id = N / tenant_id=N（大小写不敏感） */
    private static boolean matchesTenantEquals(String sql, long tenantId) {
        Pattern p = Pattern.compile(
                "(?i)tenant_id\\s*=\\s*" + tenantId + "\\b");
        return p.matcher(sql).find();
    }
}
