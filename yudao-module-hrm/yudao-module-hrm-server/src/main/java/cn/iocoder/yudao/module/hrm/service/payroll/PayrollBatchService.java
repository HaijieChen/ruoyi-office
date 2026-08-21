package cn.iocoder.yudao.module.hrm.service.payroll;

import cn.iocoder.yudao.module.hrm.dal.dataobject.payroll.PayrollBatchDO;
import cn.iocoder.yudao.module.hrm.dal.dataobject.payroll.PayrollLineDO;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

public interface PayrollBatchService {

    record PunchUploadVO(int matched, List<String> unmatched) {
    }

    PayrollBatchDO getOrCreate(int yearMonth);

    PayrollBatchDO generate(int yearMonth);

    PayrollBatchDO publish(int yearMonth);

    PayrollBatchDO withdraw(int yearMonth);

    List<PayrollLineDO> listHrLines(int yearMonth);

    List<PayrollLineDO> listMyPayslips(Long loginUserId);

    void updateAdjust(Long lineId, BigDecimal tax, BigDecimal overtime);

    PunchUploadVO uploadPunch(int yearMonth, InputStream in) throws Exception;
}
