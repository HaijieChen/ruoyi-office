package cn.iocoder.yudao.module.finance.dal.mysql.expense;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.finance.dal.dataobject.expense.FinanceExpenseReimbursementLineDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface FinanceExpenseReimbursementLineMapper extends BaseMapperX<FinanceExpenseReimbursementLineDO> {

    default List<FinanceExpenseReimbursementLineDO> selectByReimbursementId(Long reimbursementId) {
        return selectList(new LambdaQueryWrapperX<FinanceExpenseReimbursementLineDO>()
                .eq(FinanceExpenseReimbursementLineDO::getReimbursementId, reimbursementId)
                .orderByAsc(FinanceExpenseReimbursementLineDO::getSort)
                .orderByAsc(FinanceExpenseReimbursementLineDO::getId));
    }
}
