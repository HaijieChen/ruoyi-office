package cn.iocoder.yudao.module.hrm.service.payroll;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.hrm.dal.dataobject.payroll.MinWageDO;
import cn.iocoder.yudao.module.hrm.dal.mysql.payroll.MinWageMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.List;

@Service
@Validated
public class MinWageServiceImpl implements MinWageService {

    @Resource
    private MinWageMapper minWageMapper;

    @Override
    public BigDecimal effectiveOn(int yearMonth) {
        List<MinWageDO> rows = minWageMapper.selectList(new LambdaQueryWrapperX<MinWageDO>()
                .le(MinWageDO::getEffectiveMonth, yearMonth)
                .orderByDesc(MinWageDO::getEffectiveMonth)
                .orderByDesc(MinWageDO::getId));
        return rows.isEmpty() ? null : rows.get(0).getAmount();
    }

    @Override
    public void create(BigDecimal amount, boolean nextMonth, int currentYearMonth) {
        MinWageDO row = new MinWageDO();
        row.setAmount(amount);
        row.setEffectiveMonth(nextMonth ? nextYearMonth(currentYearMonth) : currentYearMonth);
        minWageMapper.insert(row);
    }

    @Override
    public List<MinWageDO> history() {
        return minWageMapper.selectList(new LambdaQueryWrapperX<MinWageDO>()
                .orderByDesc(MinWageDO::getEffectiveMonth)
                .orderByDesc(MinWageDO::getId));
    }

    static int nextYearMonth(int yearMonth) {
        int year = yearMonth / 100;
        int month = yearMonth % 100;
        if (month == 12) {
            return (year + 1) * 100 + 1;
        }
        return year * 100 + month + 1;
    }
}
