package cn.iocoder.yudao.module.hrm.dal.mysql.payroll;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.hrm.dal.dataobject.payroll.PayrollLineDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PayrollLineMapper extends BaseMapperX<PayrollLineDO> {
}
