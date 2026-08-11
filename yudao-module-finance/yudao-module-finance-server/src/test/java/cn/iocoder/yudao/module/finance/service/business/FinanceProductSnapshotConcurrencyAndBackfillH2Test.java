package cn.iocoder.yudao.module.finance.service.business;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EXP-70 第二轮：H2 作为 Mapper 谓词补充（非交付脚本执行证据）。
 * 覆盖：占用 CAS 合同+snapshot 严格匹配；回填后历史行产品/来源保持 NULL。
 */
class FinanceProductSnapshotConcurrencyAndBackfillH2Test {

    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        conn = DriverManager.getConnection(
                "jdbc:h2:mem:exp70_cas2;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa", "");
        try (Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS finance_invoice_application_line");
            st.execute("DROP TABLE IF EXISTS finance_invoice_application");
            st.execute("DROP TABLE IF EXISTS finance_business_order");
            st.execute("DROP TABLE IF EXISTS finance_contract_application");
            st.execute("""
                    CREATE TABLE finance_contract_application (
                      id BIGINT PRIMARY KEY,
                      product_type VARCHAR(64),
                      deleted BOOLEAN DEFAULT FALSE
                    )
                    """);
            st.execute("""
                    CREATE TABLE finance_business_order (
                      id BIGINT PRIMARY KEY,
                      contract_application_id BIGINT,
                      product_name VARCHAR(255),
                      product_type_snapshot VARCHAR(64),
                      settlement_amount DECIMAL(18,2) NOT NULL,
                      invoiced_occupied_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
                      deleted BOOLEAN DEFAULT FALSE
                    )
                    """);
            st.execute("""
                    CREATE TABLE finance_invoice_application (
                      id BIGINT PRIMARY KEY,
                      tax_content VARCHAR(255),
                      deleted BOOLEAN DEFAULT FALSE
                    )
                    """);
            st.execute("""
                    CREATE TABLE finance_invoice_application_line (
                      id BIGINT PRIMARY KEY,
                      application_id BIGINT NOT NULL,
                      business_order_id BIGINT NOT NULL,
                      source_contract_application_id BIGINT,
                      product_type_snapshot VARCHAR(64),
                      deleted BOOLEAN DEFAULT FALSE
                    )
                    """);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null) {
            conn.close();
        }
    }

    @Test
    void occupyCasFailsWhenContractChangedBetweenReadAndOccupy() throws Exception {
        try (Statement st = conn.createStatement()) {
            st.execute("INSERT INTO finance_contract_application(id, product_type, deleted) VALUES (1, '软件', FALSE)");
            st.execute("INSERT INTO finance_contract_application(id, product_type, deleted) VALUES (2, '硬件', FALSE)");
            st.execute("""
                    INSERT INTO finance_business_order
                    (id, contract_application_id, product_name, product_type_snapshot,
                     settlement_amount, invoiced_occupied_amount, deleted)
                    VALUES (10, 1, '软件', '软件', 100.00, 0.00, FALSE)
                    """);
            st.execute("""
                    UPDATE finance_business_order
                    SET contract_application_id = 2, product_type_snapshot = '硬件', product_name = '硬件'
                    WHERE id = 10
                    """);
        }
        int updated;
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE finance_business_order
                SET invoiced_occupied_amount = invoiced_occupied_amount + ?
                WHERE id = ?
                  AND settlement_amount - invoiced_occupied_amount >= ?
                  AND contract_application_id IS NOT NULL
                  AND contract_application_id = ?
                  AND product_type_snapshot IS NOT NULL AND TRIM(product_type_snapshot) <> ''
                  AND product_type_snapshot = ?
                  AND deleted = FALSE
                """)) {
            ps.setBigDecimal(1, new BigDecimal("30.00"));
            ps.setLong(2, 10L);
            ps.setBigDecimal(3, new BigDecimal("30.00"));
            ps.setLong(4, 1L);
            ps.setString(5, "软件");
            updated = ps.executeUpdate();
        }
        assertEquals(0, updated);
    }

    @Test
    void occupyCasRejectsLegacyNameWithoutSnapshot() throws Exception {
        try (Statement st = conn.createStatement()) {
            st.execute("INSERT INTO finance_contract_application(id, product_type, deleted) VALUES (1, '软件', FALSE)");
            st.execute("""
                    INSERT INTO finance_business_order
                    (id, contract_application_id, product_name, product_type_snapshot,
                     settlement_amount, invoiced_occupied_amount, deleted)
                    VALUES (10, 1, '自由文本', NULL, 100.00, 0.00, FALSE)
                    """);
        }
        int updated;
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE finance_business_order
                SET invoiced_occupied_amount = invoiced_occupied_amount + ?
                WHERE id = ?
                  AND settlement_amount - invoiced_occupied_amount >= ?
                  AND contract_application_id = ?
                  AND product_type_snapshot IS NOT NULL AND TRIM(product_type_snapshot) <> ''
                  AND product_type_snapshot = ?
                  AND deleted = FALSE
                """)) {
            ps.setBigDecimal(1, new BigDecimal("10.00"));
            ps.setLong(2, 10L);
            ps.setBigDecimal(3, new BigDecimal("10.00"));
            ps.setLong(4, 1L);
            ps.setString(5, "自由文本");
            updated = ps.executeUpdate();
        }
        assertEquals(0, updated, "P1 #3: cannot occupy with legacy name as expected product");
    }

    @Test
    void afterContractChangeHistoricalLineStaysUnknownOnBackfillSemantics() throws Exception {
        // 反例：终态开票 A → BO 改挂 B → 回填不得写行/表头为 B
        try (Statement st = conn.createStatement()) {
            st.execute("INSERT INTO finance_contract_application(id, product_type, deleted) VALUES (1, '软件', FALSE)");
            st.execute("INSERT INTO finance_contract_application(id, product_type, deleted) VALUES (2, '硬件', FALSE)");
            st.execute("""
                    INSERT INTO finance_business_order
                    (id, contract_application_id, product_name, product_type_snapshot,
                     settlement_amount, invoiced_occupied_amount, deleted)
                    VALUES (10, 2, '硬件', '硬件', 100.00, 0.00, FALSE)
                    """);
            st.execute("INSERT INTO finance_invoice_application(id, tax_content, deleted) VALUES (100, NULL, FALSE)");
            st.execute("""
                    INSERT INTO finance_invoice_application_line
                    (id, application_id, business_order_id, source_contract_application_id, product_type_snapshot, deleted)
                    VALUES (1000, 100, 10, NULL, NULL, FALSE)
                    """);
        }
        // 交付脚本语义：不对 line 做 product/source 回填；表头仅当行已有 snapshot
        Path root = findRepositoryRoot();
        String sql = Files.readString(root.resolve("sql/mysql/finance_product_snapshot_backfill_exp70.sql"))
                .toLowerCase(Locale.ROOT);
        assertFalse(sql.contains("set l.product_type_snapshot"),
                "delivery backfill must not set line product from current BO");
        assertFalse(sql.contains("set l.source_contract_application_id"));

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT product_type_snapshot, source_contract_application_id, tax_content "
                             + "FROM finance_invoice_application_line l "
                             + "JOIN finance_invoice_application a ON a.id = l.application_id WHERE l.id=1000")) {
            assertTrue(rs.next());
            assertNull(rs.getString(1));
            assertNull(rs.getObject(2));
        }
        // 表头回填条件不满足（行无 snapshot）——模拟仅行级条件
        int header = 0;
        try (Statement st = conn.createStatement()) {
            header = st.executeUpdate("""
                    UPDATE finance_invoice_application a
                    SET tax_content = '硬件'
                    WHERE a.id = 100
                      AND (a.tax_content IS NULL OR TRIM(a.tax_content) = '')
                      AND NOT EXISTS (
                        SELECT 1 FROM finance_invoice_application_line l
                        WHERE l.application_id = a.id AND l.deleted = FALSE
                          AND (l.product_type_snapshot IS NULL OR TRIM(l.product_type_snapshot) = '')
                      )
                    """);
        }
        assertEquals(0, header, "header must stay empty when any line product unproven");
    }

    @Test
    void backfillScriptHasAhAuditsAndNoLineProductUpdate() throws Exception {
        Path root = findRepositoryRoot();
        String sql = Files.readString(root.resolve("sql/mysql/finance_product_snapshot_backfill_exp70.sql"));
        String lower = sql.toLowerCase(Locale.ROOT);
        assertTrue(sql.contains("H_line_product_unproven"));
        assertTrue(sql.contains("G_line_source_contract_unproven"));
        assertTrue(sql.contains("I_bo_contract_authority_invalid"));
        assertTrue(lower.contains("approval_status") && lower.contains("approved"));
        assertTrue(lower.contains("importer_id") && lower.contains("applicant_user_id"));
        assertFalse(lower.contains("set l.product_type_snapshot"));
        assertTrue(lower.contains("f_bo_snapshot_still_empty_with_contract"));
        assertTrue(lower.contains("group_concat(id"));
        assertTrue(lower.contains("left join finance_contract_application"));
        assertTrue(lower.contains("settlement_amount > ifnull"));
    }

    @Test
    void auditECountsBlankHeaderWhenLinesHaveSameCompleteSnapshot() throws Exception {
        // 复审 #1 blank-header fixture：行快照完整同产品、表头空 → E 必须 > 0
        try (Statement st = conn.createStatement()) {
            st.execute("INSERT INTO finance_invoice_application(id, tax_content, deleted) VALUES (200, NULL, FALSE)");
            st.execute("""
                    INSERT INTO finance_invoice_application_line
                    (id, application_id, business_order_id, source_contract_application_id, product_type_snapshot, deleted)
                    VALUES (2001, 200, 10, 1, '软件', FALSE),
                           (2002, 200, 11, 1, '软件', FALSE)
                    """);
        }
        // 旧 E 谓词（要求 tax_content 非空）会漏报；新谓词含空表头
        String ePredicateOld = """
                SELECT COUNT(*) FROM finance_invoice_application a
                INNER JOIN (
                    SELECT application_id, MAX(TRIM(product_type_snapshot)) AS product_key
                    FROM finance_invoice_application_line
                    WHERE deleted = FALSE
                      AND product_type_snapshot IS NOT NULL AND TRIM(product_type_snapshot) <> ''
                    GROUP BY application_id
                    HAVING COUNT(DISTINCT TRIM(product_type_snapshot)) = 1
                       AND SUM(CASE WHEN product_type_snapshot IS NULL OR TRIM(product_type_snapshot) = '' THEN 1 ELSE 0 END) = 0
                ) d ON d.application_id = a.id
                WHERE a.deleted = FALSE
                  AND a.tax_content IS NOT NULL AND TRIM(a.tax_content) <> ''
                  AND TRIM(a.tax_content) <> d.product_key
                """;
        String ePredicateNew = """
                SELECT COUNT(*) FROM finance_invoice_application a
                INNER JOIN (
                    SELECT application_id, MAX(TRIM(product_type_snapshot)) AS product_key
                    FROM finance_invoice_application_line
                    WHERE deleted = FALSE
                      AND product_type_snapshot IS NOT NULL AND TRIM(product_type_snapshot) <> ''
                    GROUP BY application_id
                    HAVING COUNT(DISTINCT TRIM(product_type_snapshot)) = 1
                       AND SUM(CASE WHEN product_type_snapshot IS NULL OR TRIM(product_type_snapshot) = '' THEN 1 ELSE 0 END) = 0
                ) d ON d.application_id = a.id
                WHERE a.deleted = FALSE
                  AND (
                        a.tax_content IS NULL OR TRIM(a.tax_content) = ''
                     OR TRIM(a.tax_content) <> d.product_key
                  )
                """;
        try (Statement st = conn.createStatement()) {
            try (ResultSet oldRs = st.executeQuery(ePredicateOld)) {
                assertTrue(oldRs.next());
                assertEquals(0, oldRs.getInt(1), "old E wrongly ignores blank header");
            }
            try (ResultSet newRs = st.executeQuery(ePredicateNew)) {
                assertTrue(newRs.next());
                assertEquals(1, newRs.getInt(1), "new E must count blank-header complete-lines app");
            }
        }
    }

    @Test
    void auditICountsMissingContractAndNullApplicant() throws Exception {
        // 复审 #3：缺失合同 / NULL applicant 须进入 I（LEFT JOIN）
        try (Statement st = conn.createStatement()) {
            st.execute("""
                    ALTER TABLE finance_contract_application
                    ADD COLUMN IF NOT EXISTS approval_status VARCHAR(32);
                    """);
            st.execute("""
                    ALTER TABLE finance_contract_application
                    ADD COLUMN IF NOT EXISTS voided BOOLEAN DEFAULT FALSE;
                    """);
            st.execute("""
                    ALTER TABLE finance_contract_application
                    ADD COLUMN IF NOT EXISTS applicant_user_id BIGINT;
                    """);
            st.execute("""
                    ALTER TABLE finance_business_order
                    ADD COLUMN IF NOT EXISTS importer_id BIGINT;
                    """);
            st.execute("INSERT INTO finance_contract_application(id, product_type, approval_status, voided, applicant_user_id, deleted) "
                    + "VALUES (91, '软件', 'APPROVED', FALSE, NULL, FALSE)");
            st.execute("""
                    INSERT INTO finance_business_order
                    (id, contract_application_id, product_name, product_type_snapshot,
                     settlement_amount, invoiced_occupied_amount, importer_id, deleted)
                    VALUES
                    (901, 999, NULL, NULL, 100.00, 0.00, 1, FALSE),
                    (902, 91, NULL, NULL, 100.00, 0.00, 1, FALSE)
                    """);
        }
        String iSql = """
                SELECT COUNT(*) FROM finance_business_order bo
                LEFT JOIN finance_contract_application ca
                    ON ca.id = bo.contract_application_id AND ca.deleted = FALSE
                WHERE bo.deleted = FALSE
                  AND bo.contract_application_id IS NOT NULL
                  AND (bo.product_type_snapshot IS NULL OR TRIM(bo.product_type_snapshot) = '')
                  AND (
                        ca.id IS NULL
                     OR ca.approval_status IS NULL
                     OR ca.approval_status <> 'APPROVED'
                     OR COALESCE(ca.voided, FALSE) = TRUE
                     OR bo.importer_id IS NULL
                     OR ca.applicant_user_id IS NULL
                     OR bo.importer_id <> ca.applicant_user_id
                     OR ca.product_type IS NULL
                     OR TRIM(ca.product_type) = ''
                  )
                """;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(iSql)) {
            assertTrue(rs.next());
            assertEquals(2, rs.getInt(1), "missing contract + NULL applicant must both count in I");
        }
    }

    @Test
    void auditFboOnlyCountsOpenableWithValidAuthority() throws Exception {
        // 复审 #2：F_bo 仅可开且权威有效；无效权威不进 F_bo（归 I）
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE finance_contract_application ADD COLUMN IF NOT EXISTS approval_status VARCHAR(32)");
            st.execute("ALTER TABLE finance_contract_application ADD COLUMN IF NOT EXISTS voided BOOLEAN DEFAULT FALSE");
            st.execute("ALTER TABLE finance_contract_application ADD COLUMN IF NOT EXISTS applicant_user_id BIGINT");
            st.execute("ALTER TABLE finance_business_order ADD COLUMN IF NOT EXISTS importer_id BIGINT");
            st.execute("INSERT INTO finance_contract_application(id, product_type, approval_status, voided, applicant_user_id, deleted) "
                    + "VALUES (81, '软件', 'APPROVED', FALSE, 1, FALSE)");
            st.execute("INSERT INTO finance_contract_application(id, product_type, approval_status, voided, applicant_user_id, deleted) "
                    + "VALUES (82, '软件', 'REJECTED', FALSE, 1, FALSE)");
            st.execute("""
                    INSERT INTO finance_business_order
                    (id, contract_application_id, product_name, product_type_snapshot,
                     settlement_amount, invoiced_occupied_amount, importer_id, deleted)
                    VALUES
                    (801, 81, NULL, NULL, 100.00, 0.00, 1, FALSE),
                    (802, 81, NULL, NULL, 100.00, 100.00, 1, FALSE),
                    (803, 82, NULL, NULL, 100.00, 0.00, 1, FALSE)
                    """);
        }
        String fSql = """
                SELECT COUNT(*) FROM finance_business_order bo
                INNER JOIN finance_contract_application ca
                    ON ca.id = bo.contract_application_id AND ca.deleted = FALSE
                WHERE bo.deleted = FALSE
                  AND bo.contract_application_id IS NOT NULL
                  AND (bo.product_type_snapshot IS NULL OR TRIM(bo.product_type_snapshot) = '')
                  AND bo.settlement_amount > COALESCE(bo.invoiced_occupied_amount, 0)
                  AND ca.approval_status = 'APPROVED'
                  AND COALESCE(ca.voided, FALSE) = FALSE
                  AND bo.importer_id IS NOT NULL
                  AND ca.applicant_user_id IS NOT NULL
                  AND bo.importer_id = ca.applicant_user_id
                  AND ca.product_type IS NOT NULL
                  AND TRIM(ca.product_type) <> ''
                """;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(fSql)) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1), "only openable+valid-authority empty snapshot BO counts in F_bo");
        }
    }

    private static Path findRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        for (Path p = current; p != null; p = p.getParent()) {
            if (Files.exists(p.resolve("sql/mysql"))
                    && Files.exists(p.resolve("yudao-module-finance"))) {
                return p;
            }
        }
        throw new IllegalStateException("cannot locate repository root from " + current);
    }
}
