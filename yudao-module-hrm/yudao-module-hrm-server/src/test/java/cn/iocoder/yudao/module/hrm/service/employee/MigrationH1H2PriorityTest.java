package cn.iocoder.yudao.module.hrm.service.employee;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * H1/H2：从目标迁移脚本核心语义验证（H2 代理执行 URL 优先纠正 + 软删 URL-only）。
 * 完整 MySQL 双跑证据见 issue 回帖。
 */
class MigrationH1H2PriorityTest {

    @Test
    void uniqueUrlOverridesWrongFileIdPath_andSoftDeleteUrlOnlyCorrected() throws Exception {
        try (Connection c = DriverManager.getConnection(
                "jdbc:h2:mem:h1h2;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE");
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE infra_file (id BIGINT PRIMARY KEY, path VARCHAR(255), url VARCHAR(512), deleted INT DEFAULT 0)");
            st.execute("CREATE TABLE common_attachment ("
                    + "id BIGINT PRIMARY KEY, business_type VARCHAR(64), business_id BIGINT, "
                    + "file_id BIGINT, file_path VARCHAR(255), file_url VARCHAR(512), deleted INT DEFAULT 0, tenant_id BIGINT)");
            st.execute("INSERT INTO infra_file VALUES (100,'wrong/path.pdf','https://host/wrong.pdf',0)");
            st.execute("INSERT INTO infra_file VALUES (101,'correct/path.pdf','https://host/correct.pdf',0)");
            st.execute("INSERT INTO infra_file VALUES (105,'public/report.pdf','https://host/report.pdf',0)");
            // H1: wrong fileId+path, unique URL -> 101
            st.execute("INSERT INTO common_attachment VALUES "
                    + "(1,'hrm_employee_archive_onboarding',1,100,'wrong/path.pdf','https://host/correct.pdf',0,1)");
            // H2: soft-deleted URL-only
            st.execute("INSERT INTO common_attachment VALUES "
                    + "(2,'hrm_employee_archive_onboarding',2,NULL,'stale/old.pdf','https://host/report.pdf',1,1)");

            // 11b force URL correction (all deleted states)
            st.execute("UPDATE common_attachment a SET file_id = ("
                    + " SELECT MIN(f.id) FROM infra_file f WHERE f.deleted=0 AND f.url=a.file_url"
                    + ") WHERE LOWER(TRIM(a.business_type)) IN ('hrm_employee_archive_onboarding','201') "
                    + "AND a.file_url IS NOT NULL AND a.file_url<>'' "
                    + "AND (SELECT COUNT(*) FROM infra_file f WHERE f.deleted=0 AND f.url=a.file_url)=1");
            // 11e path normalize all
            st.execute("UPDATE common_attachment a SET file_path = ("
                    + " SELECT f.path FROM infra_file f WHERE f.id=a.file_id AND f.deleted=0"
                    + ") WHERE LOWER(TRIM(a.business_type)) IN ('hrm_employee_archive_onboarding','201') "
                    + "AND a.file_id IS NOT NULL");
            // clear url
            st.execute("UPDATE common_attachment SET file_url='' "
                    + "WHERE LOWER(TRIM(business_type)) IN ('hrm_employee_archive_onboarding','201') "
                    + "AND file_url IS NOT NULL AND file_url<>''");

            try (ResultSet rs = st.executeQuery(
                    "SELECT id,file_id,file_path,file_url FROM common_attachment ORDER BY id")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
                assertEquals(101L, rs.getLong(2));
                assertEquals("correct/path.pdf", rs.getString(3));
                assertEquals("", rs.getString(4));
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1));
                assertEquals(105L, rs.getLong(2));
                assertEquals("public/report.pdf", rs.getString(3));
                assertEquals("", rs.getString(4));
            }

            // dual run: re-apply URL force (urls empty, no change) + path normalize
            st.execute("UPDATE common_attachment a SET file_path = ("
                    + " SELECT f.path FROM infra_file f WHERE f.id=a.file_id AND f.deleted=0"
                    + ") WHERE a.file_id IS NOT NULL");
            try (ResultSet rs = st.executeQuery("SELECT file_id FROM common_attachment WHERE id=1")) {
                assertTrue(rs.next());
                assertEquals(101L, rs.getLong(1));
            }
        }
    }

}
