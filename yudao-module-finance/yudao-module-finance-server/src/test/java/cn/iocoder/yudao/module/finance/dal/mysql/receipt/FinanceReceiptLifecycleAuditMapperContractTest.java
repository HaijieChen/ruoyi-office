package cn.iocoder.yudao.module.finance.dal.mysql.receipt;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.finance.dal.dataobject.receipt.FinanceReceiptLifecycleAuditDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class FinanceReceiptLifecycleAuditMapperContractTest {

    @Test
    void mapperShouldBeImmutableInsertAndSelectOnly() {
        assertFalse(BaseMapperX.class.isAssignableFrom(FinanceReceiptLifecycleAuditMapper.class));
        assertFalse(BaseMapper.class.isAssignableFrom(FinanceReceiptLifecycleAuditMapper.class));
        Set<String> methodNames = Arrays.stream(FinanceReceiptLifecycleAuditMapper.class.getDeclaredMethods())
                .map(Method::getName).collect(Collectors.toSet());
        assertEquals(Set.of("insert", "selectListByReceiptId"), methodNames);
        assertTrue(methodNames.stream().noneMatch(name -> name.startsWith("update") || name.startsWith("delete")));
    }

    @Test
    void mapperMethodsShouldUseExplicitImmutableSql() throws NoSuchMethodException {
        Method insert = FinanceReceiptLifecycleAuditMapper.class
                .getMethod("insert", FinanceReceiptLifecycleAuditDO.class);
        Method select = FinanceReceiptLifecycleAuditMapper.class
                .getMethod("selectListByReceiptId", Long.class);

        assertNotNull(insert.getAnnotation(Insert.class));
        assertNotNull(select.getAnnotation(Select.class));
        String sql = select.getAnnotation(Select.class).value()[0];
        assertTrue(sql.contains("deleted = b'0'"));
        assertTrue(sql.contains("ORDER BY action_time DESC, id DESC"));
    }
}
