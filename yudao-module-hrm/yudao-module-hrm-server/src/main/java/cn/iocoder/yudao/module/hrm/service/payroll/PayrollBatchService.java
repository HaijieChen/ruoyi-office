package cn.iocoder.yudao.module.hrm.service.payroll;

import cn.iocoder.yudao.module.hrm.dal.dataobject.payroll.PayrollBatchDO;
import cn.iocoder.yudao.module.hrm.dal.dataobject.payroll.PayrollLineDO;

import java.math.BigDecimal;
import java.util.List;

public interface PayrollBatchService {

    PayrollBatchDO getOrCreate(int yearMonth);

    PayrollBatchDO generate(int yearMonth);

    PayrollBatchDO publish(int yearMonth);

    PayrollBatchDO withdraw(int yearMonth);

    List<PayrollLineDO> listHrLines(int yearMonth);

    List<PayrollLineDO> listMyPayslips(Long loginUserId);

    void updateAdjust(Long lineId, BigDecimal tax, BigDecimal overtime);
}
