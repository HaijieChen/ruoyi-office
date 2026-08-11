package cn.iocoder.yudao.module.hrm.service.employee;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #1：历史 file_id 唯一身份规则 — 重复 path 时精确 URL 胜出；歧义保持 NULL；可重跑纠错。
 * <p>
 * 使用 H2 标量子查询表达与迁移脚本等价的规则（MySQL 用 UPDATE JOIN，语义一致）。
 */
class FileIdentityUniquenessTest {

    @Test
    void uniqueUrlWinsOverAmbiguousPath_andRerunCorrectsWrongBinding() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:file_id_unique;DB_CLOSE_DELAY=-1");
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE infra_file ("
                    + "id BIGINT PRIMARY KEY, path VARCHAR(255), url VARCHAR(512), deleted INT DEFAULT 0)");
            st.execute("CREATE TABLE common_attachment ("
                    + "id BIGINT PRIMARY KEY, business_type VARCHAR(64), file_path VARCHAR(255), "
                    + "file_url VARCHAR(512), file_id BIGINT, deleted INT DEFAULT 0)");

            // 同 path 两条，仅一条 URL 精确匹配
            st.execute("INSERT INTO infra_file VALUES (1, 'shared/path.pdf', 'https://a/wrong', 0)");
            st.execute("INSERT INTO infra_file VALUES (2, 'shared/path.pdf', 'https://a/correct', 0)");
            // 错误绑定到 path-only 猜中的 id=1
            st.execute("INSERT INTO common_attachment VALUES "
                    + "(10, 'hrm_employee_archive_onboarding', 'shared/path.pdf', 'https://a/correct', 1, 0)");

            // 纠错：若唯一 URL 候选与当前不一致 → 清空
            st.execute("UPDATE common_attachment a SET file_id = NULL "
                    + "WHERE a.business_type='hrm_employee_archive_onboarding' AND a.deleted=0 "
                    + "AND a.file_id IS NOT NULL "
                    + "AND a.file_url IS NOT NULL AND a.file_url <> '' "
                    + "AND EXISTS ("
                    + "  SELECT 1 FROM ("
                    + "    SELECT url AS u, MIN(id) AS fid FROM infra_file WHERE deleted=0 AND url IS NOT NULL "
                    + "    GROUP BY url HAVING COUNT(*)=1"
                    + "  ) cand WHERE cand.u = a.file_url AND cand.fid <> a.file_id"
                    + ")");

            // 唯一 URL 回填
            st.execute("UPDATE common_attachment a SET file_id = ("
                    + "  SELECT cand.fid FROM ("
                    + "    SELECT url AS u, MIN(id) AS fid FROM infra_file WHERE deleted=0 AND url IS NOT NULL "
                    + "    GROUP BY url HAVING COUNT(*)=1"
                    + "  ) cand WHERE cand.u = a.file_url"
                    + ") "
                    + "WHERE a.business_type='hrm_employee_archive_onboarding' AND a.deleted=0 "
                    + "AND a.file_id IS NULL "
                    + "AND a.file_url IS NOT NULL AND a.file_url <> '' "
                    + "AND EXISTS ("
                    + "  SELECT 1 FROM ("
                    + "    SELECT url AS u, MIN(id) AS fid FROM infra_file WHERE deleted=0 AND url IS NOT NULL "
                    + "    GROUP BY url HAVING COUNT(*)=1"
                    + "  ) cand WHERE cand.u = a.file_url"
                    + ")");

            try (ResultSet rs = st.executeQuery("SELECT file_id FROM common_attachment WHERE id=10")) {
                assertTrue(rs.next());
                assertEquals(2L, rs.getLong(1));
            }

            // 歧义 path、无 URL：保持 NULL（path 不唯一则不回填）
            st.execute("INSERT INTO common_attachment VALUES "
                    + "(11, 'hrm_employee_archive_onboarding', 'shared/path.pdf', '', NULL, 0)");
            st.execute("UPDATE common_attachment a SET file_id = ("
                    + "  SELECT cand.fid FROM ("
                    + "    SELECT path AS p, MIN(id) AS fid FROM infra_file WHERE deleted=0 AND path IS NOT NULL "
                    + "    GROUP BY path HAVING COUNT(*)=1"
                    + "  ) cand WHERE cand.p = a.file_path"
                    + ") "
                    + "WHERE a.business_type='hrm_employee_archive_onboarding' AND a.deleted=0 "
                    + "AND a.file_id IS NULL AND a.file_path IS NOT NULL AND a.file_path <> '' "
                    + "AND EXISTS ("
                    + "  SELECT 1 FROM ("
                    + "    SELECT path AS p, MIN(id) AS fid FROM infra_file WHERE deleted=0 AND path IS NOT NULL "
                    + "    GROUP BY path HAVING COUNT(*)=1"
                    + "  ) cand WHERE cand.p = a.file_path"
                    + ")");
            try (ResultSet rs = st.executeQuery("SELECT file_id FROM common_attachment WHERE id=11")) {
                assertTrue(rs.next());
                assertNull(rs.getObject(1));
            }

            // 软删候选不参与
            st.execute("INSERT INTO infra_file VALUES (3, 'only/soft.pdf', 'https://a/soft', 1)");
            st.execute("INSERT INTO common_attachment VALUES "
                    + "(12, 'hrm_employee_archive_onboarding', 'only/soft.pdf', 'https://a/soft', NULL, 0)");
            st.execute("UPDATE common_attachment a SET file_id = ("
                    + "  SELECT cand.fid FROM ("
                    + "    SELECT url AS u, MIN(id) AS fid FROM infra_file WHERE deleted=0 AND url IS NOT NULL "
                    + "    GROUP BY url HAVING COUNT(*)=1"
                    + "  ) cand WHERE cand.u = a.file_url"
                    + ") "
                    + "WHERE a.business_type='hrm_employee_archive_onboarding' AND a.deleted=0 "
                    + "AND a.file_id IS NULL "
                    + "AND EXISTS ("
                    + "  SELECT 1 FROM ("
                    + "    SELECT url AS u, MIN(id) AS fid FROM infra_file WHERE deleted=0 AND url IS NOT NULL "
                    + "    GROUP BY url HAVING COUNT(*)=1"
                    + "  ) cand WHERE cand.u = a.file_url"
                    + ")");
            try (ResultSet rs = st.executeQuery("SELECT file_id FROM common_attachment WHERE id=12")) {
                assertTrue(rs.next());
                assertNull(rs.getObject(1));
            }
        }
    }

}
