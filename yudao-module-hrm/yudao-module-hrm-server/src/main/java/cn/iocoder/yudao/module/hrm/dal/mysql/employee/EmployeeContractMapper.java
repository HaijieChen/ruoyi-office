package cn.iocoder.yudao.module.hrm.dal.mysql.employee;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.EmployeeContractDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 员工合同明细 Mapper
 */
@Mapper
public interface EmployeeContractMapper extends BaseMapperX<EmployeeContractDO> {

    static LambdaQueryWrapper<EmployeeContractDO> buildEmployeeQuery(Long employeeId) {
        return new LambdaQueryWrapperX<EmployeeContractDO>()
                .eq(EmployeeContractDO::getEmployeeId, employeeId)
                .orderByAsc(EmployeeContractDO::getSequenceNo);
    }

    default List<EmployeeContractDO> selectListByEmployeeId(Long employeeId) {
        return selectList(buildEmployeeQuery(employeeId));
    }

    default List<EmployeeContractDO> selectListByEmployeeIds(Collection<Long> employeeIds) {
        if (employeeIds == null || employeeIds.isEmpty()) {
            return Collections.emptyList();
        }
        return selectList(new LambdaQueryWrapperX<EmployeeContractDO>()
                .in(EmployeeContractDO::getEmployeeId, employeeIds)
                .orderByAsc(EmployeeContractDO::getEmployeeId)
                .orderByAsc(EmployeeContractDO::getSequenceNo));
    }

    default void deleteByEmployeeId(Long employeeId) {
        delete(new LambdaQueryWrapperX<EmployeeContractDO>()
                .eq(EmployeeContractDO::getEmployeeId, employeeId));
    }

}
