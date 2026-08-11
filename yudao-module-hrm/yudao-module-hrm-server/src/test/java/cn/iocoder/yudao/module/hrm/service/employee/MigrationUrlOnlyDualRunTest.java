package cn.iocoder.yudao.module.hrm.service.employee;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #1：URL-only 绑定后 path 规范化再清 URL；连跑两次仍保持正确 file_id。
 */
class MigrationUrlOnlyDualRunTest {

    private static void runOnce(Statement st) throws Exception {
        // 11c URL backfill
        st.execute("UPDATE common_attachment a SET file_id = ("
                + " SELECT MIN(f.id) FROM infra_file f WHERE f.deleted=0 AND f.url = a.file_url"
                + ") WHERE a.business_type='hrm_employee_archive_onboarding' AND a.deleted=0 "
                + "AND a.file_id IS NULL AND a.file_url IS NOT NULL AND a.file_url <> '' "
                + "AND (SELECT COUNT(*) FROM infra_file f WHERE f.deleted=0 AND f.url=a.file_url)=1");
        // 11e path normalize
        st.execute("UPDATE common_attachment a SET file_path = ("
                + " SELECT f.path FROM infra_file f WHERE f.id=a.file_id AND f.deleted=0"
                + ") WHERE a.business_type='hrm_employee_archive_onboarding' AND a.deleted=0 "
                + "AND a.file_id IS NOT NULL");
        // clear non-authoritative: keep if file_id+path match
        st.execute("UPDATE common_attachment a SET file_id = NULL "
                + "WHERE a.business_type='hrm_employee_archive_onboarding' AND a.deleted=0 "
                + "AND a.file_id IS NOT NULL "
                + "AND NOT EXISTS (SELECT 1 FROM infra_file f WHERE f.id=a.file_id AND f.deleted=0 "
                + "  AND f.path = a.file_path)");
        // clear url
        st.execute("UPDATE common_attachment SET file_url='' "
                + "WHERE business_type='hrm_employee_archive_onboarding' AND file_url IS NOT NULL AND file_url<>''");
    }

    @Test
    void urlOnlyBindingSurvivesUrlClear_andDualRun() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:url_only_dual;DB_CLOSE_DELAY=-1;MODE=MySQL");
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE infra_file (id BIGINT PRIMARY KEY, path VARCHAR(255), url VARCHAR(512), deleted INT DEFAULT 0)");
            st.execute("CREATE TABLE common_attachment (id BIGINT PRIMARY KEY, business_type VARCHAR(64), "
                    + "file_path VARCHAR(255), file_url VARCHAR(512), file_id BIGINT, deleted INT DEFAULT 0)");
            // URL-only: stale/ambiguous path, unique URL
            st.execute("INSERT INTO infra_file VALUES (7, 'canonical/path.pdf', 'https://host/unique.pdf', 0)");
            st.execute("INSERT INTO infra_file VALUES (8, 'shared/stale.pdf', 'https://other/x.pdf', 0)");
            st.execute("INSERT INTO infra_file VALUES (9, 'shared/stale.pdf', 'https://other/y.pdf', 0)");
            st.execute("INSERT INTO common_attachment VALUES "
                    + "(1, 'hrm_employee_archive_onboarding', 'shared/stale.pdf', 'https://host/unique.pdf', NULL, 0)");

            runOnce(st);
            try (ResultSet rs = st.executeQuery("SELECT file_id, file_path, file_url FROM common_attachment WHERE id=1")) {
                assertTrue(rs.next());
                assertEquals(7L, rs.getLong(1));
                assertEquals("canonical/path.pdf", rs.getString(2));
                assertEquals("", rs.getString(3));
            }

            // 第二次完整执行不得清空
            runOnce(st);
            try (ResultSet rs = st.executeQuery("SELECT file_id, file_path FROM common_attachment WHERE id=1")) {
                assertTrue(rs.next());
                assertEquals(7L, rs.getLong(1));
                assertEquals("canonical/path.pdf", rs.getString(2));
            }
        }
    }

}
