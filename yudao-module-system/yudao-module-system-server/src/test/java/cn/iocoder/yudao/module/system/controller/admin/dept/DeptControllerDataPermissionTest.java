package cn.iocoder.yudao.module.system.controller.admin.dept;

import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 回归：部门精简列表是下拉参考数据，SELF 数据范围用户也必须能读到部门链，
 * 否则报销发起页主体公司带不出（生产事故 2026-09-03）。
 */
public class DeptControllerDataPermissionTest {

    @Test
    public void testSimpleDeptListIgnoresDataPermission() throws NoSuchMethodException {
        Method method = DeptController.class.getMethod("getSimpleDeptList");
        DataPermission annotation = method.getAnnotation(DataPermission.class);
        assertNotNull(annotation, "getSimpleDeptList 必须豁免数据权限");
        assertFalse(annotation.enable());
    }

}
