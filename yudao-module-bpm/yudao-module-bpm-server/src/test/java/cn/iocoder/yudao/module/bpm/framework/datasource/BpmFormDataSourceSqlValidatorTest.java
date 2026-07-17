package cn.iocoder.yudao.module.bpm.framework.datasource;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link BpmFormDataSourceSqlValidator} 的单元测试
 */
class BpmFormDataSourceSqlValidatorTest {

    private BpmFormDataSourceSqlValidator validator;

    @BeforeEach
    void setUp() {
        validator = new BpmFormDataSourceSqlValidator();
    }

    // ==================== 拒绝不安全 SQL ====================

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE oa_seal SET status=1",
            "DELETE FROM oa_seal",
            "SELECT 1; SELECT 2",
            "SELECT * FROM oa_seal -- bypass",
            "SELECT LOAD_FILE('/etc/passwd')",
            "CALL dangerous_proc()"
    })
    void rejectsUnsafeSql(String sql) {
        assertThrows(ServiceException.class,
                () -> validator.validateAndExtractParameters(sql));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void rejectsBlankSql(String sql) {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> validator.validateAndExtractParameters(sql));
        assertEquals(BPM_DATA_SOURCE_SQL_INVALID.getCode(), ex.getCode());
    }

    // ==================== 拒绝危险函数 ====================

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT SLEEP(5)",
            "SELECT BENCHMARK(1000000, SHA1('test'))",
            "SELECT GET_LOCK('lock1', 10)",
            "SELECT RELEASE_LOCK('lock1')",
            "SELECT * FROM oa_seal INTO OUTFILE '/tmp/data.csv'"
    })
    void rejectsDangerousFunctions(String sql) {
        assertThrows(ServiceException.class,
                () -> validator.validateAndExtractParameters(sql));
    }

    // ==================== 拒绝多语句 / 注释 ====================

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT 1; DROP TABLE oa_seal",
            "SELECT /* comment */ * FROM oa_seal",
            "SELECT * FROM oa_seal # inline comment"
    })
    void rejectsMultiStatementAndComments(String sql) {
        assertThrows(ServiceException.class,
                () -> validator.validateAndExtractParameters(sql));
    }

    // ==================== 接受合法 SELECT ====================

    @Test
    void acceptsSimpleSelect() {
        Set<String> params = validator.validateAndExtractParameters(
                "SELECT id, name FROM oa_seal WHERE company_id = :companyId AND tenant_id = :tenantId");
        assertTrue(params.contains("companyId"));
        assertTrue(params.contains("tenantId"));
        assertEquals(2, params.size());
    }

    @Test
    void acceptsWithCteSelect() {
        Set<String> params = validator.validateAndExtractParameters(
                "WITH cte AS (SELECT id FROM oa_seal WHERE tenant_id = :tenantId) " +
                "SELECT * FROM cte WHERE company_id = :companyId");
        assertTrue(params.contains("companyId"));
        assertTrue(params.contains("tenantId"));
        assertEquals(2, params.size());
    }

    @Test
    void extractsParametersInStableOrder() {
        Set<String> params = validator.validateAndExtractParameters(
                "SELECT * FROM t WHERE a = :alpha AND b = :beta AND c = :alpha");
        // :alpha appears twice but should be deduplicated
        assertEquals(Set.of("alpha", "beta"), params);
    }

    @Test
    void acceptsSelectWithNoParameters() {
        Set<String> params = validator.validateAndExtractParameters(
                "SELECT id, name FROM oa_seal WHERE status = 1");
        assertTrue(params.isEmpty());
    }

    // ==================== INSERT/DROP/ALTER 等也应拒绝 ====================

    @ParameterizedTest
    @ValueSource(strings = {
            "INSERT INTO oa_seal (name) VALUES ('test')",
            "DROP TABLE oa_seal",
            "ALTER TABLE oa_seal ADD COLUMN x INT",
            "TRUNCATE TABLE oa_seal",
            "CREATE TABLE evil (id INT)"
    })
    void rejectsDmlAndDdl(String sql) {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> validator.validateAndExtractParameters(sql));
        assertEquals(BPM_DATA_SOURCE_SQL_READ_ONLY.getCode(), ex.getCode());
    }
}
