package cn.iocoder.yudao.module.hrm.framework.datapermission.config;

import cn.iocoder.yudao.framework.datapermission.core.rule.dept.DeptDataPermissionRuleCustomizer;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.EmployeeDO;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class HrmDataPermissionConfiguration {

    @Bean
    public DeptDataPermissionRuleCustomizer hrmDeptDataPermissionRuleCustomizer() {
        return rule -> {
            rule.addDeptColumn(EmployeeDO.class, "dept_id");
            rule.addDeptColumn(EmployeeDO.class, "company_id");
            rule.addUserColumn(EmployeeDO.class, "user_id");
        };
    }
}
