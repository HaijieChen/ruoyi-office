package cn.iocoder.yudao.module.infra.service.file;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FilePageReqVO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileMapper;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClient;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * H3 生产等价跨租户单链路：
 * 真实 {@link TenantLineInnerInterceptor}/{@link TenantDatabaseInterceptor}
 * + 真实 {@link FileMapper} + {@link FileServiceImpl}
 * → 租户 B 对租户 A reserved 绑定文件：分页不可见 / get·content·delete 拒绝 / FileClient 0 调用。
 * <p>
 * 不拆成「仅 mapper」与「仅 mock 服务」两段伪证据。
 */
class FileReservedCrossTenantSingleChainTest {

    private static final long TENANT_A = 100L;
    private static final long TENANT_B = 200L;
    private static final long FILE_ID = 777L;
    private static final String FILE_PATH = "public/shared-report.pdf";

    private static SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    static void init() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(
                "jdbc:h2:mem:exp75_h3_single;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;NON_KEYWORDS=VALUE");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            st.execute("""
                    CREATE TABLE infra_file (
                      id BIGINT PRIMARY KEY,
                      config_id BIGINT,
                      name VARCHAR(256),
                      path VARCHAR(512),
                      url VARCHAR(1024),
                      type VARCHAR(63),
                      size BIGINT,
                      creator VARCHAR(64),
                      create_time TIMESTAMP,
                      updater VARCHAR(64),
                      update_time TIMESTAMP,
                      deleted BOOLEAN DEFAULT FALSE,
                      tenant_id BIGINT DEFAULT 0
                    )
                    """);
            st.execute("""
                    CREATE TABLE common_attachment (
                      id BIGINT PRIMARY KEY,
                      business_type VARCHAR(64),
                      business_id BIGINT,
                      file_id BIGINT,
                      file_name VARCHAR(256),
                      file_path VARCHAR(512),
                      file_url VARCHAR(1024),
                      file_size BIGINT,
                      file_type VARCHAR(63),
                      file_extension VARCHAR(32),
                      upload_time TIMESTAMP,
                      sort_order INT,
                      remark VARCHAR(512),
                      creator VARCHAR(64),
                      create_time TIMESTAMP,
                      updater VARCHAR(64),
                      update_time TIMESTAMP,
                      deleted BOOLEAN DEFAULT FALSE,
                      tenant_id BIGINT NOT NULL
                    )
                    """);
        }

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setCacheEnabled(false);
        configuration.setEnvironment(new Environment("h3", new JdbcTransactionFactory(), dataSource));

        GlobalConfig globalConfig = new GlobalConfig();
        GlobalConfig.DbConfig dbConfig = new GlobalConfig.DbConfig();
        dbConfig.setLogicDeleteField("deleted");
        dbConfig.setLogicDeleteValue("true");
        dbConfig.setLogicNotDeleteValue("false");
        globalConfig.setDbConfig(dbConfig);
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);

        // 注册表元数据：FileDO 带 @TenantIgnore → infra_file 全局；attachment 走 tenant 行
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), FileDO.class);

        TenantProperties props = new TenantProperties();
        props.setIgnoreTables(Collections.emptySet());
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(props)));
        configuration.addInterceptor(interceptor);
        configuration.addMapper(FileMapper.class);

        sqlSessionFactory = new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    @AfterEach
    void clear() throws Exception {
        TenantContextHolder.clear();
        try (SqlSession session = sqlSessionFactory.openSession(true);
             Statement st = session.getConnection().createStatement()) {
            st.execute("DELETE FROM common_attachment");
            st.execute("DELETE FROM infra_file");
        }
    }

    private FileServiceImpl newService(FileMapper mapper, FileConfigService configService) {
        FileServiceImpl svc = new FileServiceImpl();
        ReflectionTestUtils.setField(svc, "fileMapper", mapper);
        ReflectionTestUtils.setField(svc, "fileConfigService", configService);
        return svc;
    }

    private void seedTenantAActiveAndSoftDeletedBindings() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession(true);
             Statement st = session.getConnection().createStatement()) {
            // 全局文件（@TenantIgnore）
            st.execute("INSERT INTO infra_file (id, config_id, name, path, url, type, size, deleted, tenant_id) "
                    + "VALUES (777, 10, 'shared-report.pdf', 'public/shared-report.pdf', "
                    + "'https://cdn/shared-report.pdf', 'application/pdf', 100, FALSE, 0)");
            // 租户 A 活动 201 绑定（伪造抬升场景的历史/迁移可信绑定）
            st.execute("INSERT INTO common_attachment (id, business_type, business_id, file_id, file_path, "
                    + "file_url, deleted, tenant_id) VALUES "
                    + "(1, '201', 1, 777, 'public/shared-report.pdf', '', FALSE, 100)");
            // 租户 A 软删 onboarding 绑定同一 path
            st.execute("INSERT INTO common_attachment (id, business_type, business_id, file_id, file_path, "
                    + "file_url, deleted, tenant_id) VALUES "
                    + "(2, 'hrm_employee_archive_onboarding', 2, 777, 'public/shared-report.pdf', '', TRUE, 100)");
        }
    }

    @Test
    void tenantB_pageGetContentDelete_allReject_providerZeroCalls() throws Exception {
        seedTenantAActiveAndSoftDeletedBindings();

        FileConfigService configService = mock(FileConfigService.class);
        FileClient fileClient = mock(FileClient.class);
        // 若误调用 provider 会留下痕迹
        lenient().when(configService.getFileClient(anyLong())).thenReturn(fileClient);
        lenient().when(fileClient.getContent(any())).thenReturn(new byte[]{1});

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            FileMapper mapper = session.getMapper(FileMapper.class);
            FileServiceImpl service = newService(mapper, configService);

            // —— 租户 B 上下文：单链路 ——
            TenantUtils.execute(TENANT_B, () -> {
                // 1) 真实 reserved 计数（@InterceptorIgnore 跨租户）应 >0
                Long cnt = mapper.countReservedAttachmentByFileId(FILE_ID);
                assertNotNull(cnt);
                assertTrue(cnt >= 1, "tenant B must see tenant A reserved binding, cnt=" + cnt);

                // 2) 分页：全局文件被 NOT EXISTS 排除
                FilePageReqVO pageReq = new FilePageReqVO();
                pageReq.setPageNo(1);
                pageReq.setPageSize(10);
                PageResult<FileDO> page = service.getFilePage(pageReq);
                assertNotNull(page);
                boolean visible = page.getList() != null && page.getList().stream()
                        .anyMatch(f -> FILE_ID == f.getId());
                assertFalse(visible, "tenant B public page must not list reserved file 777");

                // 3) get / content / delete 均拒绝，provider 0
                assertThrows(IllegalArgumentException.class, () -> service.getFile(FILE_ID));
                assertThrows(IllegalArgumentException.class,
                        () -> service.getFileContent(10L, FILE_PATH));
                assertThrows(IllegalArgumentException.class, () -> service.deleteFile(FILE_ID));
                return null;
            });
        }

        verify(configService, never()).getFileClient(anyLong());
        verify(fileClient, never()).getContent(any());
        verify(fileClient, never()).delete(any());
    }

    @Test
    void tenantA_forgedBindingStillBlocksTenantB_whenPresent() throws Exception {
        // 显式复现审查反例：租户 A 写 201→全局 file，租户 B 拒
        try (SqlSession session = sqlSessionFactory.openSession(true);
             Statement st = session.getConnection().createStatement()) {
            st.execute("INSERT INTO infra_file (id, config_id, name, path, url, size, deleted) "
                    + "VALUES (777, 10, 'x.pdf', 'public/shared-report.pdf', 'https://x', 1, FALSE)");
            st.execute("INSERT INTO common_attachment (id, business_type, business_id, file_id, file_path, "
                    + "file_url, deleted, tenant_id) VALUES "
                    + "(9, '201', 9, 777, 'public/shared-report.pdf', '', FALSE, " + TENANT_A + ")");
        }

        FileConfigService configService = mock(FileConfigService.class);
        FileClient fileClient = mock(FileClient.class);
        lenient().when(configService.getFileClient(anyLong())).thenReturn(fileClient);

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            FileServiceImpl service = newService(session.getMapper(FileMapper.class), configService);
            TenantUtils.execute(TENANT_B, () -> {
                assertThrows(IllegalArgumentException.class, () -> service.getFile(FILE_ID));
                assertThrows(IllegalArgumentException.class, () -> service.deleteFile(FILE_ID));
                return null;
            });
        }
        verify(configService, never()).getFileClient(anyLong());
        verify(fileClient, never()).delete(any());
    }

}
