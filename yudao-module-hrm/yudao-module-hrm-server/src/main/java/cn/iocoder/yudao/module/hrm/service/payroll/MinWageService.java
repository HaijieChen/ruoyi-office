package cn.iocoder.yudao.module.hrm.service.payroll;

import cn.iocoder.yudao.module.hrm.dal.dataobject.payroll.MinWageDO;

import java.math.BigDecimal;
import java.util.List;

public interface MinWageService {

    BigDecimal effectiveOn(int yearMonth);

    void create(BigDecimal amount, boolean nextMonth, int currentYearMonth);

    List<MinWageDO> history();
}
