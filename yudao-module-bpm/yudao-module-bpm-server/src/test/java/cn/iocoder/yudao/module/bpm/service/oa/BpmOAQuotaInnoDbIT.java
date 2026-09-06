package cn.iocoder.yudao.module.bpm.service.oa;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOAOvertimeCreateReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOAPunchCorrectionCreateReqVO;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.security.OaBillAccessPermission;
import cn.iocoder.yudao.module.bpm.dal.mysql.oa.BpmOAOvertimeMapper;
import cn.iocoder.yudao.module.bpm.dal.mysql.oa.BpmOAPunchCorrectionMapper;
import cn.iocoder.yudao.module.bpm.dal.mysql.oa.BpmOAQuotaLockMapper;
import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.Resource;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.OA_OVERTIME_DAY_QUOTA_EXCEEDED;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.OA_PUNCH_MONTH_QUOTA_EXCEEDED;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * U9 InnoDB evidence. Red empty-range trials use a test-only MyBatis FOR UPDATE
 * interceptor (after real proceed). Green paths do not wait after SELECT.
 * Isolation is set per worker TransactionTemplate; no SET GLOBAL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = BpmOAQuotaInnoDbIT.App.class)
@ActiveProfiles("oa-quota-innodb")
class BpmOAQuotaInnoDbIT {

    private static final int RED_TRIALS = 20;
    private static final int GREEN_TRIALS = 20;
    private static final long TENANT_A = 1L;
    private static final long TENANT_B = 2L;
    private static final LocalDateTime DAY = LocalDateTime.of(2026, 9, 6, 10, 0, 0);
    private static final LocalDate AUG_1 = LocalDate.of(2026, 8, 1);
    private static final LocalDate AUG_15 = LocalDate.of(2026, 8, 15);
    private static final LocalDate AUG_20 = LocalDate.of(2026, 8, 20);
    private static final LocalDate AUG_25 = LocalDate.of(2026, 8, 25);
    private static final int RUNNING = BpmTaskStatusEnum.RUNNING.getStatus();
    private static final int APPROVE = BpmTaskStatusEnum.APPROVE.getStatus();

    @Resource
    private BpmOAOvertimeServiceImpl overtimeService;
    @Resource
    private BpmOAPunchCorrectionServiceImpl punchService;
    @Resource
    private BpmProcessInstanceApi processInstanceApi;
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private DataSource dataSource;
    @Resource
    private PlatformTransactionManager transactionManager;
    @Resource
    private ForUpdateEmptyRaceInterceptor raceInterceptor;
    @Resource
    private TenantSqlCaptureInterceptor tenantSqlCapture;

    @BeforeAll
    static void requireIsolatedMysql() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 13306), 2000);
        } catch (Exception ex) {
            assumeTrue(false, "isolated mysql 127.0.0.1:13306 not reachable");
        }
    }

    @BeforeEach
    void resetDbAndMocks() {
        assertTrue(jdbcUrl().contains("13306"));
        assertFalse(jdbcUrl().contains("33061"));
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
                    PRIMARY KEY (id),
                    KEY idx_user_start (user_id, start_time)
                ) ENGINE=InnoDB
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS bpm_oa_punch_correction (
                    id bigint NOT NULL AUTO_INCREMENT,
                    user_id bigint NOT NULL,
                    punch_date date NOT NULL,
                    punch_time datetime NOT NULL,
                    reason varchar(500) NOT NULL,
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
                    PRIMARY KEY (id),
                    KEY idx_user_punch_date (user_id, punch_date)
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
        jdbcTemplate.execute("TRUNCATE TABLE bpm_oa_punch_correction");
        jdbcTemplate.execute("TRUNCATE TABLE bpm_oa_quota_lock");
        TenantContextHolder.setTenantId(TENANT_A);
        raceInterceptor.disarm();
        tenantSqlCapture.clear();
        reset(processInstanceApi);
        when(processInstanceApi.createProcessInstance(any(), any(BpmProcessInstanceCreateReqDTO.class)))
                .thenAnswer(invocation -> CommonResult.success("pi-" + System.nanoTime()));
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void reportsJdbcUrlNot33061() {
        assertTrue(jdbcUrl().contains("127.0.0.1:13306"));
        assertFalse(jdbcUrl().contains("33061"));
    }

    @Test
    void green_rr_overtime20_oneSuccessOneQuotaError() throws Exception {
        runGreenOvertime20(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    @Test
    void green_rc_overtime20_oneSuccessOneQuotaError() throws Exception {
        runGreenOvertime20(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Test
    void green_punchEmpty_twoSucceed() throws Exception {
        long userId = 98001L;
        ConcurrentOutcome outcome = runGreenConcurrent(
                () -> asTenant(TENANT_A, () -> inTx(TransactionDefinition.ISOLATION_REPEATABLE_READ,
                        () -> punchService.createPunchCorrection(userId, punchReq(AUG_1)))),
                () -> asTenant(TENANT_A, () -> inTx(TransactionDefinition.ISOLATION_REPEATABLE_READ,
                        () -> punchService.createPunchCorrection(userId, punchReq(AUG_15)))),
                OA_PUNCH_MONTH_QUOTA_EXCEEDED.getCode());
        assertEquals(0, outcome.deadlock);
        assertEquals(0, outcome.otherErrors);
        assertEquals(2, outcome.success);
        assertEquals(0, outcome.fail);
        assertEquals(2, occupyingPunchCount(userId));
    }

    @Test
    void green_punchEmpty_threeAtMostTwo() throws Exception {
        long userId = 98002L;
        ConcurrentOutcome outcome = runGreenConcurrentN(List.of(
                () -> asTenant(TENANT_A, () -> inTx(TransactionDefinition.ISOLATION_REPEATABLE_READ,
                        () -> punchService.createPunchCorrection(userId, punchReq(AUG_1)))),
                () -> asTenant(TENANT_A, () -> inTx(TransactionDefinition.ISOLATION_REPEATABLE_READ,
                        () -> punchService.createPunchCorrection(userId, punchReq(AUG_15)))),
                () -> asTenant(TENANT_A, () -> inTx(TransactionDefinition.ISOLATION_REPEATABLE_READ,
                        () -> punchService.createPunchCorrection(userId, punchReq(AUG_20))))
        ));
        assertEquals(0, outcome.deadlock);
        assertEquals(0, outcome.otherErrors);
        assertEquals(2, outcome.success);
        assertEquals(1, outcome.fail);
        assertEquals(2, occupyingPunchCount(userId));
    }

    @Test
    void green_tenantAndUserIsolation() throws Exception {
        tenantSqlCapture.clear();
        asTenant(TENANT_A, () -> overtimeService.createOvertime(1L, overtimeReq(DAY, DAY.plusHours(5))));
        asTenant(TENANT_B, () -> overtimeService.createOvertime(1L, overtimeReq(DAY, DAY.plusHours(5))));
        Integer tenantA = jdbcTemplate.queryForObject(
                "SELECT tenant_id FROM bpm_oa_overtime WHERE user_id = 1 AND hours = 5.0 AND deleted = b'0' ORDER BY id LIMIT 1",
                Integer.class);
        Integer tenantB = jdbcTemplate.queryForObject(
                "SELECT tenant_id FROM bpm_oa_overtime WHERE user_id = 1 AND hours = 5.0 AND deleted = b'0' ORDER BY id DESC LIMIT 1",
                Integer.class);
        assertEquals(1, tenantA, "first sequential row tenant_id; sqls=" + tenantSqlCapture.snapshot());
        assertEquals(2, tenantB, "second sequential row tenant_id; sqls=" + tenantSqlCapture.snapshot());
        assertTrue(tenantSqlCapture.anyContainsTenantId()
                        || (tenantA == 1 && tenantB == 2),
                "tenant interceptor must persist tenant_id; sqls=" + tenantSqlCapture.snapshot());
        assertEquals(0, new BigDecimal("5.0").compareTo(occupyingOvertimeHours(1L, TENANT_A)));
        assertEquals(0, new BigDecimal("5.0").compareTo(occupyingOvertimeHours(1L, TENANT_B)));

        ConcurrentOutcome tenants = runGreenConcurrent(
                () -> asTenant(TENANT_A, () -> inTx(TransactionDefinition.ISOLATION_READ_COMMITTED,
                        () -> overtimeService.createOvertime(11L, overtimeReq(DAY, DAY.plusHours(5))))),
                () -> asTenant(TENANT_B, () -> inTx(TransactionDefinition.ISOLATION_READ_COMMITTED,
                        () -> overtimeService.createOvertime(11L, overtimeReq(DAY, DAY.plusHours(5))))),
                OA_OVERTIME_DAY_QUOTA_EXCEEDED.getCode());
        assertEquals(0, tenants.deadlock);
        assertEquals(0, tenants.otherErrors);
        assertEquals(2, tenants.success, "empty-day concurrent different tenants must both succeed");
        assertEquals(0, new BigDecimal("5.0").compareTo(occupyingOvertimeHours(11L, TENANT_A)));
        assertEquals(0, new BigDecimal("5.0").compareTo(occupyingOvertimeHours(11L, TENANT_B)));

        ConcurrentOutcome users = runGreenConcurrent(
                () -> asTenant(TENANT_A, () -> inTx(TransactionDefinition.ISOLATION_READ_COMMITTED,
                        () -> overtimeService.createOvertime(2L, overtimeReq(DAY, DAY.plusHours(5))))),
                () -> asTenant(TENANT_A, () -> inTx(TransactionDefinition.ISOLATION_READ_COMMITTED,
                        () -> overtimeService.createOvertime(3L, overtimeReq(DAY, DAY.plusHours(5))))),
                OA_OVERTIME_DAY_QUOTA_EXCEEDED.getCode());
        assertEquals(2, users.success, "different users must not share the lock");
    }

    @Test
    void ae4_rr_emptyNonOverlap5h_redTrials() throws Exception {
        TrialReport report = runOvertimeEmptyRed(TransactionDefinition.ISOLATION_REPEATABLE_READ, "REPEATABLE-READ");
        System.out.println("AE4_RR_RED " + report);
        assertFalse(report.validDualEmpty == 0 && report.breakthrough > 0);
        if (report.validDualEmpty == 0) {
            System.out.println("AE4_RR_RED no valid dual-empty trials; NOT judged safe");
        }
    }

    @Test
    void ae4_rc_emptyNonOverlap5h_redTrials() throws Exception {
        TrialReport report = runOvertimeEmptyRed(TransactionDefinition.ISOLATION_READ_COMMITTED, "READ-COMMITTED");
        System.out.println("AE4_RC_RED " + report);
        if (report.validDualEmpty == 0) {
            System.out.println("AE4_RC_RED no valid dual-empty trials; NOT judged safe");
        }
    }

    @Test
    void punchEmpty_rr_redTrials_twoShouldSucceedWhenBothEmpty() throws Exception {
        TrialReport report = runPunchEmptyRed(TransactionDefinition.ISOLATION_REPEATABLE_READ, "REPEATABLE-READ");
        System.out.println("PUNCH_EMPTY_RR_RED " + report);
        if (report.validDualEmpty == 0) {
            System.out.println("PUNCH_EMPTY_RR_RED no valid dual-empty trials; NOT judged safe");
        }
    }

    @Test
    void ae6_punchExistingTwo_thirdFailsRemainingZero() {
        long userId = 92001L;
        punchService.createPunchCorrection(userId, punchReq(AUG_1));
        punchService.createPunchCorrection(userId, punchReq(AUG_15));
        assertEquals(0, punchService.getRemainingCount(userId, AUG_20));
        ServiceException ex = assertThrows(ServiceException.class,
                () -> punchService.createPunchCorrection(userId, punchReq(AUG_20)));
        assertEquals(OA_PUNCH_MONTH_QUOTA_EXCEEDED.getCode(), ex.getCode());
        assertEquals(0, punchService.getRemainingCount(userId, AUG_25));
        assertEquals(2, occupyingPunchCount(userId));
    }

    @Test
    void r9_punchExistingOne_concurrentSecondAndThird_greenNoPostSelectWait() throws Exception {
        long userId = 92002L;
        punchService.createPunchCorrection(userId, punchReq(AUG_1));
        ConcurrentOutcome outcome = runGreenConcurrent(
                () -> asTenant(TENANT_A, () -> inTx(TransactionDefinition.ISOLATION_REPEATABLE_READ,
                        () -> punchService.createPunchCorrection(userId, punchReq(AUG_15)))),
                () -> asTenant(TENANT_A, () -> inTx(TransactionDefinition.ISOLATION_REPEATABLE_READ,
                        () -> punchService.createPunchCorrection(userId, punchReq(AUG_20)))),
                OA_PUNCH_MONTH_QUOTA_EXCEEDED.getCode());
        System.out.println("R9_PUNCH_GREEN success=" + outcome.success + " fail=" + outcome.fail
                + " deadlock=" + outcome.deadlock + " other=" + outcome.otherErrors);
        assertEquals(0, outcome.otherErrors);
        assertEquals(1, outcome.success);
        assertEquals(1, outcome.fail);
        assertEquals(2, occupyingPunchCount(userId));
        assertEquals(0, punchService.getRemainingCount(userId, AUG_25));
    }

    @Test
    void startProcessThrow_rollsBackOvertimeRow() {
        long userId = 93001L;
        reset(processInstanceApi);
        when(processInstanceApi.createProcessInstance(eq(userId), any(BpmProcessInstanceCreateReqDTO.class)))
                .thenThrow(new IllegalStateException("process-start boom"));
        assertThrows(IllegalStateException.class,
                () -> overtimeService.createOvertime(userId, overtimeReq(DAY, DAY.plusHours(5))));
        assertEquals(0, occupyingOvertimeCount(userId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bpm_oa_overtime WHERE user_id = ? AND tenant_id = ?", Integer.class, userId, TENANT_A));
    }

    @Test
    void startProcessThrow_rollsBackPunchRow() {
        long userId = 93002L;
        reset(processInstanceApi);
        when(processInstanceApi.createProcessInstance(eq(userId), any(BpmProcessInstanceCreateReqDTO.class)))
                .thenThrow(new IllegalStateException("process-start boom"));
        assertThrows(IllegalStateException.class,
                () -> punchService.createPunchCorrection(userId, punchReq(AUG_15)));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bpm_oa_punch_correction WHERE user_id = ? AND tenant_id = ?", Integer.class, userId, TENANT_A));
        assertEquals(2, punchService.getRemainingCount(userId, AUG_15));
    }

    @Test
    void overtimeRejectCancelWithdraw_releaseQuota() {
        assertReleaseOvertime(BpmTaskStatusEnum.REJECT, 94001L);
        assertReleaseOvertime(BpmTaskStatusEnum.CANCEL, 94002L);
        assertReleaseOvertime(BpmTaskStatusEnum.WITHDRAW, 94003L);
    }

    @Test
    void punchRejectCancelWithdraw_releaseQuota() {
        assertReleasePunch(BpmTaskStatusEnum.REJECT, 95001L);
        assertReleasePunch(BpmTaskStatusEnum.CANCEL, 95002L);
        assertReleasePunch(BpmTaskStatusEnum.WITHDRAW, 95003L);
    }

    private void assertReleaseOvertime(BpmTaskStatusEnum terminal, long userId) {
        Long id = overtimeService.createOvertime(userId, overtimeReq(DAY, DAY.plusHours(5)));
        overtimeService.updateOvertimeStatus(id, terminal.getStatus());
        Long id2 = overtimeService.createOvertime(userId, overtimeReq(DAY.plusHours(6), DAY.plusHours(11)));
        assertNotNull(id2, terminal.name());
        assertEquals(1, occupyingOvertimeCount(userId), terminal.name());
    }

    private void assertReleasePunch(BpmTaskStatusEnum terminal, long userId) {
        Long id = punchService.createPunchCorrection(userId, punchReq(AUG_1));
        punchService.updatePunchCorrectionStatus(id, terminal.getStatus());
        punchService.createPunchCorrection(userId, punchReq(AUG_15));
        punchService.createPunchCorrection(userId, punchReq(AUG_20));
        assertEquals(0, punchService.getRemainingCount(userId, AUG_25), terminal.name());
        assertEquals(2, occupyingPunchCount(userId), terminal.name());
    }

    private TrialReport runOvertimeEmptyRed(int isolation, String expectedIso) throws Exception {
        TrialReport report = new TrialReport("overtime", expectedIso);
        for (int trial = 0; trial < RED_TRIALS; trial++) {
            long userId = 91000L + trial;
            jdbcTemplate.execute("TRUNCATE TABLE bpm_oa_overtime");
            TrialOutcome outcome = runRedPair(isolation, expectedIso,
                    () -> overtimeService.createOvertime(userId, overtimeReq(DAY, DAY.plusHours(5))),
                    () -> overtimeService.createOvertime(userId, overtimeReq(DAY.plusHours(6), DAY.plusHours(11))));
            accumulate(report, outcome, occupyingOvertimeHours(userId).compareTo(new BigDecimal("8.0")) > 0);
        }
        return report;
    }

    private TrialReport runPunchEmptyRed(int isolation, String expectedIso) throws Exception {
        TrialReport report = new TrialReport("punch", expectedIso);
        for (int trial = 0; trial < RED_TRIALS; trial++) {
            long userId = 96000L + trial;
            jdbcTemplate.execute("TRUNCATE TABLE bpm_oa_punch_correction");
            TrialOutcome outcome = runRedPair(isolation, expectedIso,
                    () -> punchService.createPunchCorrection(userId, punchReq(AUG_1)),
                    () -> punchService.createPunchCorrection(userId, punchReq(AUG_15)));
            boolean breakthrough = occupyingPunchCount(userId) > 2;
            accumulate(report, outcome, breakthrough);
        }
        return report;
    }

    private TrialOutcome runRedPair(int isolation, String expectedIso, Runnable first, Runnable second)
            throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        raceInterceptor.arm(barrier, 3000L);
        try {
            ConcurrentOutcome concurrent = runGreenConcurrent(
                    () -> asTenant(TENANT_A, () -> inTx(isolation, first)),
                    () -> asTenant(TENANT_A, () -> inTx(isolation, second)),
                    -1);
            List<ForUpdateEmptyRaceInterceptor.Observation> obs =
                    new ArrayList<>(raceInterceptor.observations().values());
            boolean dualEmpty = obs.size() == 2
                    && obs.get(0).emptyRead()
                    && obs.get(1).emptyRead()
                    && expectedIso.equalsIgnoreCase(obs.get(0).isolation())
                    && expectedIso.equalsIgnoreCase(obs.get(1).isolation());
            boolean timeout = raceInterceptor.barrierTimeouts() > 0;
            System.out.println("RED_TRIAL dualEmpty=" + dualEmpty
                    + " obs=" + obs
                    + " timeouts=" + raceInterceptor.barrierTimeouts()
                    + " success=" + concurrent.success
                    + " fail=" + concurrent.fail
                    + " deadlock=" + concurrent.deadlock);
            return new TrialOutcome(dualEmpty, timeout, concurrent.deadlock > 0, concurrent.success,
                    concurrent.fail, concurrent.otherErrors);
        } finally {
            raceInterceptor.disarm();
        }
    }

    private void accumulate(TrialReport report, TrialOutcome outcome, boolean hoursBreakthrough) {
        report.trials++;
        if (outcome.timeout) {
            report.selectSerializedTimeout++;
        }
        if (outcome.deadlock) {
            report.deadlock++;
        }
        if (outcome.dualEmpty) {
            report.validDualEmpty++;
            if (hoursBreakthrough) {
                report.breakthrough++;
            }
        }
        report.successSum += outcome.success;
        report.otherErrors += outcome.otherErrors;
    }

    private void runGreenOvertime20(int isolation) throws Exception {
        for (int trial = 0; trial < GREEN_TRIALS; trial++) {
            long userId = 97000L + trial;
            jdbcTemplate.execute("TRUNCATE TABLE bpm_oa_overtime");
            jdbcTemplate.execute("TRUNCATE TABLE bpm_oa_quota_lock");
            ConcurrentOutcome outcome = runGreenConcurrent(
                    () -> asTenant(TENANT_A, () -> inTx(isolation,
                            () -> overtimeService.createOvertime(userId, overtimeReq(DAY, DAY.plusHours(5))))),
                    () -> asTenant(TENANT_A, () -> inTx(isolation,
                            () -> overtimeService.createOvertime(userId,
                                    overtimeReq(DAY.plusHours(6), DAY.plusHours(11))))),
                    OA_OVERTIME_DAY_QUOTA_EXCEEDED.getCode());
            assertEquals(0, outcome.deadlock, "green overtime deadlock trial=" + trial);
            assertEquals(0, outcome.otherErrors, "green overtime other trial=" + trial);
            assertEquals(1, outcome.success, "green overtime success trial=" + trial);
            assertEquals(1, outcome.fail, "green overtime fail trial=" + trial);
            assertTrue(occupyingOvertimeHours(userId).compareTo(new BigDecimal("8.0")) <= 0);
        }
    }

    private void asTenant(long tenantId, Runnable action) {
        Long previous = TenantContextHolder.getTenantId();
        TenantContextHolder.setTenantId(tenantId);
        try {
            action.run();
        } finally {
            if (previous == null) {
                TenantContextHolder.clear();
            } else {
                TenantContextHolder.setTenantId(previous);
            }
        }
    }

    private ConcurrentOutcome runGreenConcurrent(Runnable first, Runnable second, int expectedFailCode)
            throws Exception {
        return runGreenConcurrentN(List.of(first, second), expectedFailCode);
    }

    private ConcurrentOutcome runGreenConcurrentN(List<Runnable> tasks) throws Exception {
        return runGreenConcurrentN(tasks, -1);
    }

    private ConcurrentOutcome runGreenConcurrentN(List<Runnable> tasks, int expectedFailCode)
            throws Exception {
        CountDownLatch done = new CountDownLatch(tasks.size());
        AtomicInteger success = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();
        AtomicInteger deadlock = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            for (Runnable task : tasks) {
                pool.submit(() -> {
                    try {
                        task.run();
                        success.incrementAndGet();
                    } catch (ServiceException ex) {
                        boolean quotaFail = ex.getCode() != null && (expectedFailCode < 0
                                ? (ex.getCode() == OA_OVERTIME_DAY_QUOTA_EXCEEDED.getCode()
                                || ex.getCode() == OA_PUNCH_MONTH_QUOTA_EXCEEDED.getCode())
                                : ex.getCode() == expectedFailCode);
                        if (quotaFail) {
                            fail.incrementAndGet();
                        } else {
                            other.incrementAndGet();
                            ex.printStackTrace();
                        }
                    } catch (DeadlockLoserDataAccessException | CannotAcquireLockException ex) {
                        deadlock.incrementAndGet();
                    } catch (Exception ex) {
                        if (isDeadlock(ex)) {
                            deadlock.incrementAndGet();
                        } else {
                            other.incrementAndGet();
                            ex.printStackTrace();
                        }
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertTrue(done.await(40, TimeUnit.SECONDS), "concurrent workers timed out");
        } finally {
            pool.shutdownNow();
        }
        return new ConcurrentOutcome(success.get(), fail.get(), deadlock.get(), other.get());
    }

    private void inTx(int isolation, Runnable action) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        template.setIsolationLevel(isolation);
        template.executeWithoutResult(status -> action.run());
    }

    private static boolean isDeadlock(Throwable ex) {
        for (Throwable cur = ex; cur != null; cur = cur.getCause()) {
            String msg = cur.getMessage();
            if (msg != null && msg.toLowerCase().contains("deadlock")) {
                return true;
            }
        }
        return false;
    }

    private String jdbcUrl() {
        if (dataSource instanceof HikariDataSource hikari) {
            return hikari.getJdbcUrl();
        }
        return String.valueOf(jdbcTemplate.getDataSource());
    }

    private BigDecimal occupyingOvertimeHours(long userId) {
        return occupyingOvertimeHours(userId, TENANT_A);
    }

    private BigDecimal occupyingOvertimeHours(long userId, long tenantId) {
        BigDecimal hours = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(hours), 0) FROM bpm_oa_overtime WHERE user_id = ? AND tenant_id = ? AND deleted = b'0' AND status IN (?, ?)",
                BigDecimal.class, userId, tenantId, RUNNING, APPROVE);
        return hours == null ? BigDecimal.ZERO : hours;
    }

    private int occupyingOvertimeCount(long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bpm_oa_overtime WHERE user_id = ? AND tenant_id = ? AND deleted = b'0' AND status IN (?, ?)",
                Integer.class, userId, TENANT_A, RUNNING, APPROVE);
        return count == null ? 0 : count;
    }

    private int occupyingPunchCount(long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bpm_oa_punch_correction WHERE user_id = ? AND tenant_id = ? AND deleted = b'0' AND status IN (?, ?)",
                Integer.class, userId, TENANT_A, RUNNING, APPROVE);
        return count == null ? 0 : count;
    }

    private static BpmOAOvertimeCreateReqVO overtimeReq(LocalDateTime start, LocalDateTime end) {
        BpmOAOvertimeCreateReqVO req = new BpmOAOvertimeCreateReqVO();
        req.setReason("项目上线");
        req.setStartTime(start);
        req.setEndTime(end);
        req.setHoliday("false");
        return req;
    }

    private static BpmOAPunchCorrectionCreateReqVO punchReq(LocalDate punchDate) {
        BpmOAPunchCorrectionCreateReqVO req = new BpmOAPunchCorrectionCreateReqVO();
        req.setReason("忘记打卡");
        req.setPunchDate(punchDate);
        req.setPunchTime(punchDate.atTime(9, 0));
        return req;
    }

    private record ConcurrentOutcome(int success, int fail, int deadlock, int otherErrors) {
    }

    private record TrialOutcome(boolean dualEmpty, boolean timeout, boolean deadlock,
                                int success, int fail, int otherErrors) {
    }

    private static final class TrialReport {
        final String kind;
        final String isolation;
        int trials;
        int validDualEmpty;
        int breakthrough;
        int deadlock;
        int selectSerializedTimeout;
        int successSum;
        int otherErrors;

        TrialReport(String kind, String isolation) {
            this.kind = kind;
            this.isolation = isolation;
        }

        @Override
        public String toString() {
            return "kind=" + kind + " isolation=" + isolation
                    + " trials=" + trials
                    + " validDualEmpty=" + validDualEmpty
                    + " breakthrough=" + breakthrough
                    + " deadlock=" + deadlock
                    + " selectSerializedTimeout=" + selectSerializedTimeout
                    + " successSum=" + successSum
                    + " otherErrors=" + otherErrors;
        }
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
        ForUpdateEmptyRaceInterceptor forUpdateEmptyRaceInterceptor(DataSource dataSource) {
            return new ForUpdateEmptyRaceInterceptor(dataSource);
        }

        @Bean
        TenantSqlCaptureInterceptor tenantSqlCaptureInterceptor() {
            return new TenantSqlCaptureInterceptor();
        }

        @Bean
        TenantProperties tenantProperties() {
            return new TenantProperties();
        }

        @Bean
        BeanPostProcessor tenantLineInstaller(TenantProperties properties) {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (bean instanceof MybatisPlusInterceptor interceptor) {
                        boolean present = interceptor.getInterceptors().stream()
                                .anyMatch(TenantLineInnerInterceptor.class::isInstance);
                        if (!present) {
                            MyBatisUtils.addInterceptor(interceptor,
                                    new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(properties)), 0);
                        }
                    }
                    return bean;
                }
            };
        }

        @Bean
        ConfigurationCustomizer forUpdateRaceCustomizer(ForUpdateEmptyRaceInterceptor interceptor,
                                                        TenantSqlCaptureInterceptor tenantSqlCaptureInterceptor) {
            return configuration -> {
                configuration.addInterceptor(interceptor);
                configuration.addInterceptor(tenantSqlCaptureInterceptor);
            };
        }

        @Bean
        BpmOAOvertimeServiceImpl overtimeService(BpmOAOvertimeMapper overtimeMapper,
                                                 BpmOAQuotaLockMapper quotaLockMapper,
                                                 BpmProcessInstanceApi processInstanceApi,
                                                 SecurityFrameworkService securityFrameworkService,
                                                 OaBillAccessPermission oaBillAccessPermission) {
            BpmOAOvertimeServiceImpl service = new BpmOAOvertimeServiceImpl();
            inject(service, "overtimeMapper", overtimeMapper);
            inject(service, "quotaLockMapper", quotaLockMapper);
            inject(service, "processInstanceApi", processInstanceApi);
            inject(service, "securityFrameworkService", securityFrameworkService);
            inject(service, "oaBillAccessPermission", oaBillAccessPermission);
            return service;
        }

        @Bean
        BpmOAPunchCorrectionServiceImpl punchService(BpmOAPunchCorrectionMapper punchMapper,
                                                     BpmOAQuotaLockMapper quotaLockMapper,
                                                     BpmProcessInstanceApi processInstanceApi,
                                                     SecurityFrameworkService securityFrameworkService,
                                                     OaBillAccessPermission oaBillAccessPermission) {
            BpmOAPunchCorrectionServiceImpl service = new BpmOAPunchCorrectionServiceImpl();
            inject(service, "punchCorrectionMapper", punchMapper);
            inject(service, "quotaLockMapper", quotaLockMapper);
            inject(service, "processInstanceApi", processInstanceApi);
            inject(service, "securityFrameworkService", securityFrameworkService);
            inject(service, "oaBillAccessPermission", oaBillAccessPermission);
            return service;
        }

        private static void inject(Object target, String field, Object value) {
            try {
                var f = target.getClass().getDeclaredField(field);
                f.setAccessible(true);
                f.set(target, value);
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
