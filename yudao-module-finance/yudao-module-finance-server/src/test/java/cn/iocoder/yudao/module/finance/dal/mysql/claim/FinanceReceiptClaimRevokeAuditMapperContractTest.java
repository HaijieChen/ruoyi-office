package cn.iocoder.yudao.module.finance.dal.mysql.claim;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.finance.dal.dataobject.claim.FinanceReceiptClaimRevokeAuditDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class FinanceReceiptClaimRevokeAuditMapperContractTest {

    @Test
    void mapperShouldNotExtendBaseMapperXOrBaseMapper() {
        assertFalse(BaseMapperX.class.isAssignableFrom(FinanceReceiptClaimRevokeAuditMapper.class),
                "FinanceReceiptClaimRevokeAuditMapper must NOT extend BaseMapperX");
        assertFalse(BaseMapper.class.isAssignableFrom(FinanceReceiptClaimRevokeAuditMapper.class),
                "FinanceReceiptClaimRevokeAuditMapper must NOT extend BaseMapper");
    }

    @Test
    void mapperShouldOnlyHaveInsertAndSelectMethods() {
        Set<String> methodNames = Arrays.stream(FinanceReceiptClaimRevokeAuditMapper.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertEquals(Set.of("insert", "selectListByClaimId"), methodNames,
                "Mapper must only declare insert and selectListByClaimId methods");
    }

    @Test
    void mapperShouldHaveNoUpdateOrDeleteMethods() {
        Set<String> methodNames = Arrays.stream(FinanceReceiptClaimRevokeAuditMapper.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertTrue(methodNames.stream().noneMatch(name -> name.startsWith("update")),
                "Mapper must have no update methods");
        assertTrue(methodNames.stream().noneMatch(name -> name.startsWith("delete")),
                "Mapper must have no delete methods");
    }

    @Test
    void insertMethodShouldHaveInsertAnnotation() throws NoSuchMethodException {
        Method insertMethod = FinanceReceiptClaimRevokeAuditMapper.class.getMethod(
                "insert", FinanceReceiptClaimRevokeAuditDO.class);
        assertNotNull(insertMethod.getAnnotation(Insert.class),
                "insert method must have @Insert annotation");
    }

    @Test
    void selectListByClaimIdMethodShouldHaveSelectAnnotation() throws NoSuchMethodException {
        Method selectMethod = FinanceReceiptClaimRevokeAuditMapper.class.getMethod(
                "selectListByClaimId", Long.class);
        assertNotNull(selectMethod.getAnnotation(Select.class),
                "selectListByClaimId method must have @Select annotation");
        String sql = selectMethod.getAnnotation(Select.class).value()[0];
        assertTrue(sql.contains("ORDER BY revoke_time DESC, id DESC"),
                "SQL must order by revoke_time DESC, id DESC");
        assertTrue(sql.contains("deleted = b'0'"),
                "SQL must filter soft-deleted records");
    }

}
