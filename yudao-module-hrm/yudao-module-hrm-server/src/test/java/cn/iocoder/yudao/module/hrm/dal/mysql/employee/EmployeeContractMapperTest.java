package cn.iocoder.yudao.module.hrm.dal.mysql.employee;

import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.EmployeeContractDO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EmployeeContractMapperTest {

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                EmployeeContractDO.class);
    }

    @Test
    void contractQueryOrdersBySequenceNo() {
        var wrapper = EmployeeContractMapper.buildEmployeeQuery(42L);
        assertTrue(wrapper.getSqlSegment().contains("employee_id"));
        assertTrue(wrapper.getSqlSegment().toLowerCase().contains("sequence_no"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(42L));
    }

}
