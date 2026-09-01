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

    default boolean existsInvoiceNo(String invoiceNo) {
        if (invoiceNo == null || invoiceNo.isBlank()) {
            return false;
        }
        return selectCount(new LambdaQueryWrapperX<FinanceExpenseReimbursementLineDO>()
                .eq(FinanceExpenseReimbursementLineDO::getInvoiceNo, invoiceNo.trim())) > 0;
    }

    String OCCUPIED_HEADER_IDS_SQL =
            "SELECT id FROM finance_expense_reimbursement WHERE deleted = b'0' "
                    + "AND status IN ('PENDING','WAIT_PAY','PAID')";

    default boolean existsOccupiedPredoc(String predocProcessInstanceId) {
        if (predocProcessInstanceId == null || predocProcessInstanceId.isBlank()) {
            return false;
        }
        return selectCount(new LambdaQueryWrapperX<FinanceExpenseReimbursementLineDO>()
                .eq(FinanceExpenseReimbursementLineDO::getPredocProcessInstanceId, predocProcessInstanceId.trim())
                .inSql(FinanceExpenseReimbursementLineDO::getReimbursementId, OCCUPIED_HEADER_IDS_SQL)) > 0;
    }

    default List<String> selectOccupiedPredocProcessInstanceIds() {
        return selectList(new LambdaQueryWrapperX<FinanceExpenseReimbursementLineDO>()
                .isNotNull(FinanceExpenseReimbursementLineDO::getPredocProcessInstanceId)
                .ne(FinanceExpenseReimbursementLineDO::getPredocProcessInstanceId, "")
                .inSql(FinanceExpenseReimbursementLineDO::getReimbursementId, OCCUPIED_HEADER_IDS_SQL)).stream()
                .map(FinanceExpenseReimbursementLineDO::getPredocProcessInstanceId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
    }
}
