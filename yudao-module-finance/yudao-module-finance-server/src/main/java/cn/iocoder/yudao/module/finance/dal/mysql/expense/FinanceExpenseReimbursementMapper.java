package cn.iocoder.yudao.module.finance.dal.mysql.expense;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.finance.controller.admin.expense.vo.FinanceExpenseReimbursementPageReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.expense.FinanceExpenseReimbursementDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FinanceExpenseReimbursementMapper extends BaseMapperX<FinanceExpenseReimbursementDO> {

    default PageResult<FinanceExpenseReimbursementDO> selectPage(FinanceExpenseReimbursementPageReqVO reqVO,
                                                                 Long forceApplicantId) {
        return selectPage(reqVO, new LambdaQueryWrapperX<FinanceExpenseReimbursementDO>()
                .eqIfPresent(FinanceExpenseReimbursementDO::getStatus, reqVO.getStatus())
                .eqIfPresent(FinanceExpenseReimbursementDO::getPeriodLabel, reqVO.getPeriodLabel())
                .eq(forceApplicantId != null, FinanceExpenseReimbursementDO::getApplicantUserId, forceApplicantId)
                .orderByDesc(FinanceExpenseReimbursementDO::getId));
    }
}
