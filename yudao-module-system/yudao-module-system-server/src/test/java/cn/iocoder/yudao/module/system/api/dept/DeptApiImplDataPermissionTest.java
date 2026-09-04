package cn.iocoder.yudao.module.system.api.dept;

import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 回归：内部部门 API 用于跨模块数据校验与拼装，不能把 SELF 数据范围应用到 system_dept 查询。
 */
public class DeptApiImplDataPermissionTest {

    @Test
    public void testGetDeptIgnoresDataPermission() throws NoSuchMethodException {
        assertDataPermissionDisabled("getDept", Long.class);
    }

    @Test
    public void testGetCompanySimpleListIgnoresDataPermission() throws NoSuchMethodException {
        assertDataPermissionDisabled("getCompanySimpleList");
    }

    private static void assertDataPermissionDisabled(String methodName, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = DeptApiImpl.class.getMethod(methodName, parameterTypes);
        DataPermission annotation = method.getAnnotation(DataPermission.class);
        assertNotNull(annotation, methodName + " 必须豁免数据权限");
        assertFalse(annotation.enable());
    }

}
