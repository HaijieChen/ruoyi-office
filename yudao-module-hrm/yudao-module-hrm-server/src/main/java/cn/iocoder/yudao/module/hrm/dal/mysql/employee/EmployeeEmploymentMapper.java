package cn.iocoder.yudao.module.hrm.dal.mysql.employee;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.EmployeeEmploymentDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface EmployeeEmploymentMapper extends BaseMapperX<EmployeeEmploymentDO> {

    default List<EmployeeEmploymentDO> selectListByEmployeeId(Long employeeId) {
        return selectList(new LambdaQueryWrapperX<EmployeeEmploymentDO>()
                .eq(EmployeeEmploymentDO::getEmployeeId, employeeId)
                .orderByDesc(EmployeeEmploymentDO::getSigned)
                .orderByAsc(EmployeeEmploymentDO::getId));
    }

    default void deleteByEmployeeId(Long employeeId) {
        delete(new LambdaQueryWrapperX<EmployeeEmploymentDO>()
                .eq(EmployeeEmploymentDO::getEmployeeId, employeeId));
    }
}
