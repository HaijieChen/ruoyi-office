package cn.iocoder.yudao.module.finance.controller.admin.expense.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FinanceExpenseReimbursementPageReqVO extends PageParam {

    private String status;
    private String periodLabel;
}
