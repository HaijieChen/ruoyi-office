package cn.iocoder.yudao.module.hrm.framework.datapermission.config;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.datapermission.core.rule.dept.DeptDataPermissionRule;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.EmployeeDO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import net.sf.jsqlparser.expression.Alias;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Collections;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class HrmDataPermissionConfigurationTest {

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), EmployeeDO.class);
    }

    @Test
    void selfScopeUsesEmployeeUserIdInsteadOfDenyingOwnArchive() {
        PermissionCommonApi permissionApi = mock(PermissionCommonApi.class);
        DeptDataPermissionRule rule = new DeptDataPermissionRule(permissionApi);
        new HrmDataPermissionConfiguration().hrmDeptDataPermissionRuleCustomizer().customize(rule);

        LoginUser loginUser = new LoginUser()
                .setId(683L)
                .setUserType(UserTypeEnum.ADMIN.getValue());
        DeptDataPermissionRespDTO permission = new DeptDataPermissionRespDTO()
                .setAll(false)
                .setSelf(true)
                .setDeptIds(Collections.emptySet());
        when(permissionApi.getDeptDataPermission(683L)).thenReturn(success(permission));

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUser).thenReturn(loginUser);

            assertEquals("e.user_id = 683", rule.getExpression("hrm_employee", new Alias("e")).toString());
        }
    }
}
