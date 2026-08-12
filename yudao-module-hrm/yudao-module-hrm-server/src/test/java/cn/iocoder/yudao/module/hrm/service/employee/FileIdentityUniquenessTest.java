package cn.iocoder.yudao.module.hrm.service.employee;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #3：错误非空 fileId 纠错（H2 标量子查询，语义对齐 MySQL 迁移 11a-11d）。
 */
class FileIdentityUniquenessTest {

    @Test
    void clearsWrongNonNullFileId_andRerunCorrects() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:file_id_v3;DB_CLOSE_DELAY=-1");
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE infra_file ("
                    + "id BIGINT PRIMARY KEY, path VARCHAR(255), url VARCHAR(512), deleted INT DEFAULT 0)");
            st.execute("CREATE TABLE common_attachment ("
                    + "id BIGINT PRIMARY KEY, business_type VARCHAR(64), file_path VARCHAR(255), "
                    + "file_url VARCHAR(512), file_id BIGINT, deleted INT DEFAULT 0)");

            st.execute("INSERT INTO infra_file VALUES (1, 'shared/path.pdf', 'https://a/wrong', 0)");
            st.execute("INSERT INTO infra_file VALUES (2, 'shared/path.pdf', 'https://a/correct', 0)");
            // 空 URL + 错误非空 file_id=1，path 歧义
            st.execute("INSERT INTO common_attachment VALUES "
                    + "(10, 'hrm_employee_archive_onboarding', 'shared/path.pdf', '', 1, 0)");

            // 11a missing/deleted
            st.execute("UPDATE common_attachment a SET file_id = NULL "
                    + "WHERE a.business_type='hrm_employee_archive_onboarding' AND a.deleted=0 "
                    + "AND a.file_id IS NOT NULL "
                    + "AND NOT EXISTS (SELECT 1 FROM infra_file f WHERE f.id=a.file_id AND f.deleted=0)");

            // 11b non-authoritative clear
            st.execute("UPDATE common_attachment a SET file_id = NULL "
                    + "WHERE a.business_type='hrm_employee_archive_onboarding' AND a.deleted=0 "
                    + "AND a.file_id IS NOT NULL "
                    + "AND NOT ("
                    + "  EXISTS (SELECT 1 FROM ("
                    + "    SELECT url u, MIN(id) fid FROM infra_file WHERE deleted=0 AND url IS NOT NULL "
                    + "    GROUP BY url HAVING COUNT(*)=1"
                    + "  ) cand WHERE cand.u = a.file_url AND cand.fid = a.file_id)"
                    + "  OR ("
                    + "    NOT EXISTS (SELECT 1 FROM ("
                    + "      SELECT url u FROM infra_file WHERE deleted=0 AND url IS NOT NULL "
                    + "      GROUP BY url HAVING COUNT(*)=1"
                    + "    ) cand WHERE cand.u = a.file_url)"
                    + "    AND EXISTS (SELECT 1 FROM ("
                    + "      SELECT path p, MIN(id) fid FROM infra_file WHERE deleted=0 AND path IS NOT NULL "
                    + "      GROUP BY path HAVING COUNT(*)=1"
                    + "    ) cand WHERE cand.p = a.file_path AND cand.fid = a.file_id)"
                    + "  )"
                    + ")");

            try (ResultSet rs = st.executeQuery("SELECT file_id FROM common_attachment WHERE id=10")) {
                assertTrue(rs.next());
                assertNull(rs.getObject(1)); // 歧义 path → 清空
            }

            // 精确 URL 错误绑定 → 纠正
            st.execute("INSERT INTO common_attachment VALUES "
                    + "(11, 'hrm_employee_archive_onboarding', 'shared/path.pdf', 'https://a/correct', 1, 0)");
            st.execute("UPDATE common_attachment a SET file_id = NULL "
                    + "WHERE a.id=11 AND a.file_id IS NOT NULL "
                    + "AND NOT EXISTS (SELECT 1 FROM ("
                    + "  SELECT url u, MIN(id) fid FROM infra_file WHERE deleted=0 AND url IS NOT NULL "
                    + "  GROUP BY url HAVING COUNT(*)=1"
                    + ") cand WHERE cand.u = a.file_url AND cand.fid = a.file_id)");
            st.execute("UPDATE common_attachment a SET file_id = ("
                    + "  SELECT cand.fid FROM ("
                    + "    SELECT url u, MIN(id) fid FROM infra_file WHERE deleted=0 AND url IS NOT NULL "
                    + "    GROUP BY url HAVING COUNT(*)=1"
                    + "  ) cand WHERE cand.u = a.file_url"
                    + ") WHERE a.id=11 AND a.file_id IS NULL");
            try (ResultSet rs = st.executeQuery("SELECT file_id FROM common_attachment WHERE id=11")) {
                assertTrue(rs.next());
                assertEquals(2L, rs.getLong(1));
            }

            // 续跑：权威绑定保持
            st.execute("UPDATE common_attachment a SET file_id = NULL "
                    + "WHERE a.id=11 AND a.file_id IS NOT NULL "
                    + "AND NOT EXISTS (SELECT 1 FROM ("
                    + "  SELECT url u, MIN(id) fid FROM infra_file WHERE deleted=0 AND url IS NOT NULL "
                    + "  GROUP BY url HAVING COUNT(*)=1"
                    + ") cand WHERE cand.u = a.file_url AND cand.fid = a.file_id)");
            try (ResultSet rs = st.executeQuery("SELECT file_id FROM common_attachment WHERE id=11")) {
                assertTrue(rs.next());
                assertEquals(2L, rs.getLong(1));
            }
        }
    }

}
