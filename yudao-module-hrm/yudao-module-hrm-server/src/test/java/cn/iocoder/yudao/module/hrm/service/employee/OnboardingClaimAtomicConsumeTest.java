package cn.iocoder.yudao.module.hrm.service.employee;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #4：条件 UPDATE 在双事务并发下仅允许一次消费（H2 真实 JDBC）。
 * SQL 形态与 {@code OnboardingFileClaimMapper#consumeIfOpen} 一致。
 */
class OnboardingClaimAtomicConsumeTest {

    private static final String CONSUME_SQL =
            "UPDATE hrm_onboarding_file_claim "
                    + "SET consumed_at = ?, consumed_employee_id = ? "
                    + "WHERE claim_token = ? "
                    + "AND uploader_user_id = ? "
                    + "AND purpose = ? "
                    + "AND expire_time > ? "
                    + "AND consumed_at IS NULL "
                    + "AND deleted = 0";

    @Test
    void dualTransactionOnlyOneConsumes() throws Exception {
        try (Connection bootstrap = DriverManager.getConnection("jdbc:h2:mem:claim_atomic;DB_CLOSE_DELAY=-1")) {
            try (Statement st = bootstrap.createStatement()) {
                st.execute("CREATE TABLE hrm_onboarding_file_claim ("
                        + "id BIGINT PRIMARY KEY, claim_token VARCHAR(64), file_id BIGINT, "
                        + "uploader_user_id BIGINT, purpose VARCHAR(32), expire_time TIMESTAMP, "
                        + "consumed_at TIMESTAMP, consumed_employee_id BIGINT, deleted INT DEFAULT 0)");
                st.execute("INSERT INTO hrm_onboarding_file_claim "
                        + "(id, claim_token, file_id, uploader_user_id, purpose, expire_time, consumed_at, deleted) "
                        + "VALUES (1, 'tok-concurrent', 99, 7, 'hrm-onboarding', "
                        + "DATEADD('HOUR', 2, CURRENT_TIMESTAMP), NULL, 0)");
            }

            AtomicInteger success = new AtomicInteger();
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            Future<?> f1 = pool.submit(() -> tryConsume(start, 101L, success));
            Future<?> f2 = pool.submit(() -> tryConsume(start, 202L, success));
            start.countDown();
            f1.get();
            f2.get();
            pool.shutdownNow();

            assertEquals(1, success.get(), "并发下仅一个事务应消费成功");

            try (PreparedStatement ps = bootstrap.prepareStatement(
                    "SELECT COUNT(*) FROM hrm_onboarding_file_claim WHERE consumed_at IS NOT NULL");
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertEquals(1, rs.getInt(1));
            }
            try (PreparedStatement ps = bootstrap.prepareStatement(
                    "SELECT consumed_employee_id FROM hrm_onboarding_file_claim WHERE id = 1");
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                long emp = rs.getLong(1);
                assertTrue(emp == 101L || emp == 202L);
            }
        }
    }

    private static void tryConsume(CountDownLatch start, long employeeId, AtomicInteger success) {
        try {
            start.await();
            try (Connection c = DriverManager.getConnection("jdbc:h2:mem:claim_atomic;DB_CLOSE_DELAY=-1")) {
                c.setAutoCommit(false);
                Timestamp now = Timestamp.from(Instant.now());
                try (PreparedStatement ps = c.prepareStatement(CONSUME_SQL)) {
                    ps.setTimestamp(1, now);
                    ps.setLong(2, employeeId);
                    ps.setString(3, "tok-concurrent");
                    ps.setLong(4, 7L);
                    ps.setString(5, "hrm-onboarding");
                    ps.setTimestamp(6, now);
                    int rows = ps.executeUpdate();
                    c.commit();
                    if (rows == 1) {
                        success.incrementAndGet();
                    }
                } catch (Exception ex) {
                    c.rollback();
                    throw ex;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
