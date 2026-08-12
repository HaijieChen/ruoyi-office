package cn.iocoder.yudao.module.infra.dal.mysql.file;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * H3：reserved 判定 SQL 跨租户（@InterceptorIgnore + 多 tenant_id 数据，真实 mapper）。
 */
public class FileMapperReservedCrossTenantTest extends BaseDbUnitTest {

    @Resource
    private FileMapper fileMapper;
    @Resource
    private DataSource dataSource;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void initJdbc() {
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void countReservedByFileIdSeesOtherTenantRows() throws Exception {
        boolean hasIgnore = FileMapper.class.getMethod("countReservedAttachmentByFileId", Long.class)
                .isAnnotationPresent(InterceptorIgnore.class);
        assertTrue(hasIgnore, "countReservedAttachmentByFileId must @InterceptorIgnore(tenantLine)");
        InterceptorIgnore ann = FileMapper.class.getMethod("countReservedAttachmentByFileId", Long.class)
                .getAnnotation(InterceptorIgnore.class);
        assertEquals("true", ann.tenantLine());

        // 租户 1 活动绑定 + 租户 2 软删绑定，同一 fileId
        jdbcTemplate.update(
                "INSERT INTO common_attachment (id, business_type, business_id, file_id, file_path, file_url, deleted, tenant_id) "
                        + "VALUES (9001, 'hrm_employee_archive_onboarding', 1, 501, 'p/a.pdf', '', FALSE, 1)");
        jdbcTemplate.update(
                "INSERT INTO common_attachment (id, business_type, business_id, file_id, file_path, file_url, deleted, tenant_id) "
                        + "VALUES (9002, 'hrm_employee_archive_onboarding', 2, 501, 'p/b.pdf', '', TRUE, 2)");

        Long cnt = fileMapper.countReservedAttachmentByFileId(501L);
        assertNotNull(cnt);
        assertTrue(cnt >= 2, "must count both tenants including soft-deleted, got " + cnt);

        Long pathCnt = fileMapper.countReservedAttachmentByPath("p/a.pdf");
        assertNotNull(pathCnt);
        assertTrue(pathCnt >= 1, "path reserved count, got " + pathCnt);
    }

}
