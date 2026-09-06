package cn.iocoder.yudao.module.bpm.service.oa;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetSocketAddress;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Real JDBC + Spring TM on 13306/oa_u12_iso. Independent connection reads rows.
 * Not mock Mapper / in-memory DO.
 */
class OaAttendanceTxIT {

    private static final String JDBC =
            "jdbc:mysql://127.0.0.1:13306/oa_u12_iso?useSSL=false&allowPublicKeyRetrieval=true"
                    + "&characterEncoding=utf-8&serverTimezone=Asia/Shanghai";

    private static HikariDataSource ds;
    private static PlatformTransactionManager tm;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void open() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 13306), 2000);
        } catch (Exception ex) {
            assumeTrue(false, "isolated mysql 127.0.0.1:13306 not reachable");
        }
        ds = new HikariDataSource();
        ds.setJdbcUrl(JDBC);
        ds.setUsername("root");
        ds.setMaximumPoolSize(6);
        tm = new DataSourceTransactionManager(ds);
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS oa_u12_bill (
                  id BIGINT PRIMARY KEY,
                  process_instance_id VARCHAR(64) NULL,
                  status INT NOT NULL
                ) ENGINE=InnoDB
                """);
    }

    @AfterAll
    static void close() {
        if (ds != null) {
            ds.close();
        }
    }

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM oa_u12_bill");
        jdbc.update("INSERT INTO oa_u12_bill(id, process_instance_id, status) VALUES (1, 'pi-A', 1)");
    }

    @Test
    void afterCommitRequiresNewVisibleOnIndependentConnection() {
        TransactionTemplate outer = new TransactionTemplate(tm);
        outer.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        outer.executeWithoutResult(status ->
                OaAttendanceTx.dispatchAfterCommit(tm, null, () ->
                        jdbc.update("UPDATE oa_u12_bill SET status=2 WHERE id=1")));
        assertEquals(Integer.valueOf(2), independentStatus());
    }

    @Test
    void rollbackDoesNotDispatch() {
        TransactionTemplate outer = new TransactionTemplate(tm);
        try {
            outer.executeWithoutResult(status -> {
                OaAttendanceTx.dispatchAfterCommit(tm, null, () ->
                        jdbc.update("UPDATE oa_u12_bill SET status=2 WHERE id=1"));
                throw new RuntimeException("approval-rollback");
            });
        } catch (RuntimeException ex) {
            assertEquals("approval-rollback", ex.getMessage());
        }
        assertEquals(Integer.valueOf(1), independentStatus());
    }

    @Test
    void mismatchedPiIdDoesNotUpdateOnIndependentConnection() {
        jdbc.update("UPDATE oa_u12_bill SET status=1, process_instance_id='pi-A' WHERE id=1");
        int changed = jdbc.update(
                "UPDATE oa_u12_bill SET status=2 WHERE id=1 AND process_instance_id=?",
                "pi-B");
        assertEquals(0, changed);
        assertEquals(Integer.valueOf(1), independentStatus());
    }

    @Test
    void jdbcUrlIsU12IsoNotU9OrU10() {
        assertEquals(true, JDBC.contains("oa_u12_iso"));
        assertEquals(false, JDBC.contains("oa_u9_iso"));
        assertEquals(false, JDBC.contains("oa_u10_iso"));
    }

    private static Integer independentStatus() {
        DriverManagerDataSource independent = new DriverManagerDataSource();
        independent.setUrl(JDBC);
        independent.setUsername("root");
        JdbcTemplate other = new JdbcTemplate(independent);
        return other.queryForObject("SELECT status FROM oa_u12_bill WHERE id=1", Integer.class);
    }
}
