package cn.iocoder.yudao.module.hrm.service.employee;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #2：fileId 身份必须字节精确（模拟 BINARY 语义：仅大小写不同不得匹配）。
 * <p>
 * H2 默认大小写敏感的 = 已近似 BINARY；生产 MySQL 夹具用 BINARY 再证一次。
 */
class FileIdentityBinaryExactTest {

    @Test
    void caseOnlyDifferentUrlDoesNotMatchAsUniqueExact() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:file_bin;DB_CLOSE_DELAY=-1;MODE=MySQL");
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE infra_file ("
                    + "id BIGINT PRIMARY KEY, path VARCHAR(255), url VARCHAR(512), deleted INT DEFAULT 0)");
            st.execute("CREATE TABLE common_attachment ("
                    + "id BIGINT PRIMARY KEY, business_type VARCHAR(64), "
                    + "file_path VARCHAR(255), file_url VARCHAR(512), file_id BIGINT, deleted INT DEFAULT 0)");

            // 仅大小写不同
            st.execute("INSERT INTO infra_file VALUES "
                    + "(1, 'Bucket/File.pdf', 'https://host/Bucket/File.pdf', 0)");
            st.execute("INSERT INTO common_attachment VALUES "
                    + "(10, 'hrm_employee_archive_onboarding', 'bucket/file.pdf', "
                    + "'https://host/bucket/file.pdf', NULL, 0)");

            // 唯一 URL 回填：H2 大小写敏感 → 不应匹配
            st.execute("UPDATE common_attachment a SET file_id = ("
                    + "  SELECT MIN(f.id) FROM infra_file f WHERE f.deleted=0 AND f.url = a.file_url"
                    + ") WHERE a.id=10 AND a.file_id IS NULL "
                    + "AND (SELECT COUNT(*) FROM infra_file f WHERE f.deleted=0 AND f.url = a.file_url) = 1");

            try (ResultSet rs = st.executeQuery("SELECT file_id FROM common_attachment WHERE id=10")) {
                assertTrue(rs.next());
                assertNull(rs.getObject(1), "仅大小写不同的 URL 不得回填");
            }

            // 精确相同则回填
            st.execute("UPDATE common_attachment SET file_url='https://host/Bucket/File.pdf' WHERE id=10");
            st.execute("UPDATE common_attachment a SET file_id = ("
                    + "  SELECT MIN(f.id) FROM infra_file f WHERE f.deleted=0 AND f.url = a.file_url"
                    + ") WHERE a.id=10 AND a.file_id IS NULL "
                    + "AND (SELECT COUNT(*) FROM infra_file f WHERE f.deleted=0 AND f.url = a.file_url) = 1");
            try (ResultSet rs = st.executeQuery("SELECT file_id FROM common_attachment WHERE id=10")) {
                assertTrue(rs.next());
                assertEquals(1L, rs.getLong(1));
            }
        }
    }

}
