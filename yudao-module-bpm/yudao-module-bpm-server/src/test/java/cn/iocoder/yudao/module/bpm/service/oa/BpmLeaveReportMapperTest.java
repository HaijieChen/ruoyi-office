package cn.iocoder.yudao.module.bpm.service.oa;

import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOALeavePageReqVO;
import cn.iocoder.yudao.module.bpm.dal.mysql.oa.BpmOALeaveMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.Expression;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.session.SqlSession;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BpmLeaveReportMapperTest {
    SqlSession session;
    BpmOALeaveMapper mapper;
    @BeforeEach void setup() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:leave" + UUID.randomUUID() + ";MODE=MySQL;NON_KEYWORDS=DAY;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE bpm_oa_leave (id BIGINT PRIMARY KEY,user_id BIGINT,type VARCHAR(10),reason VARCHAR(100),attachment_urls VARCHAR(100),start_time TIMESTAMP,end_time TIMESTAMP,day BIGINT,result INT,process_instance_id VARCHAR(64),create_time TIMESTAMP,update_time TIMESTAMP,creator VARCHAR(32),updater VARCHAR(32),deleted BOOLEAN DEFAULT FALSE,tenant_id BIGINT)");
        jdbc.execute("INSERT INTO bpm_oa_leave(id,user_id,type,reason,result,tenant_id) VALUES (1,1,'5','mine',2,1),(17,710,'5','report',2,1),(18,711,'4','other',1,1),(19,710,'5','other tenant',2,2)");
        var config = new MybatisConfiguration();
        config.setEnvironment(new Environment("test", new JdbcTransactionFactory(), ds));
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override public Expression getTenantId() { return new LongValue(1); }
        }));
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        config.addInterceptor(interceptor);
        config.addMapper(BpmOALeaveMapper.class);
        session = new MybatisSqlSessionFactoryBuilder().build(config).openSession();
        mapper = session.getMapper(BpmOALeaveMapper.class);
    }
    @AfterEach void close() { if (session != null) session.close(); }
    @Test void allStillTenantScoped() { assertEquals(3L, mapper.selectPageByUsers(null,new BpmOALeavePageReqVO()).getTotal()); }
    @Test void emptyScopeDoesNotBecomeAll() { assertEquals(0L, mapper.selectPageByUsers(Set.of(),new BpmOALeavePageReqVO()).getTotal()); }
    @Test void departmentAndSelfFilterActualRows() { assertEquals(2L, mapper.selectPageByUsers(Set.of(1L,710L),new BpmOALeavePageReqVO()).getTotal()); }
    @Test void mineRetainsOwnerFilter() { assertEquals(1L, mapper.selectPage(1L,new BpmOALeavePageReqVO()).getTotal()); }
    @Test void reportFiltersRemain() {
        var query = new BpmOALeavePageReqVO(); query.setStatus(2); query.setReason("report");
        var page = mapper.selectPageByUsers(null,query);
        assertEquals(1L,page.getTotal()); assertEquals(17L,page.getList().get(0).getId());
    }
}
