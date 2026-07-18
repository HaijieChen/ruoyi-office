package cn.iocoder.yudao.module.hrm.dal.mysql.employee;

import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.EmployeeDO;
import cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.EmployeeSelectPageReqVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EmployeeMapperTest {

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), EmployeeDO.class);
    }

    @Test
    void selectableEmployeesCanBeRestrictedToCompany() {
        var request = new EmployeeSelectPageReqVO().setCompanyId(42L).setDeptId(7L);

        var wrapper = EmployeeMapper.buildSelectableQuery(request);

        assertTrue(wrapper.getSqlSegment().contains("company_id"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(42L));
        assertTrue(wrapper.getSqlSegment().contains("dept_id"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(7L));
    }

    @Test
    void selectedCompanyOverridesLoginCompanyForBusinessSelection() {
        var request = new EmployeeSelectPageReqVO()
                .setCompanyId(42L)
                .setSelectedCompanyId(99L)
                .setDeptId(7L)
                .setSelectedDeptId(8L);

        var wrapper = EmployeeMapper.buildSelectableQuery(request);

        assertTrue(wrapper.getSqlSegment().contains("company_id"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(99L),
                () -> "actual query parameters: " + wrapper.getParamNameValuePairs());
        assertTrue(wrapper.getParamNameValuePairs().containsValue(8L));
        assertTrue(wrapper.getParamNameValuePairs().values().stream()
                .noneMatch(value -> Long.valueOf(42L).equals(value)));
        assertTrue(wrapper.getParamNameValuePairs().values().stream()
                .noneMatch(value -> Long.valueOf(7L).equals(value)));
    }

}
