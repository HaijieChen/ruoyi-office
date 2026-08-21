package cn.iocoder.yudao.module.hrm.dal.mysql.payroll;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.hrm.dal.dataobject.payroll.PayrollBatchDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PayrollBatchMapper extends BaseMapperX<PayrollBatchDO> {
}
