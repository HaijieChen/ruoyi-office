package cn.iocoder.yudao.module.finance.service.contract;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.finance.dal.dataobject.contract.FinanceContractApplicationDO;

import java.math.BigDecimal;

/** 销售+有金额合同审批通过后是否自动出商务单。 */
final class FinanceApprovedSalesBusinessOrderSupport {

    static final String SALES_FILE_TYPE = "销售合同";

    private FinanceApprovedSalesBusinessOrderSupport() {
    }

    static boolean shouldCreate(FinanceContractApplicationDO contract, long existingBoCount) {
        if (contract == null || existingBoCount > 0) {
            return false;
        }
        if (!SALES_FILE_TYPE.equals(StrUtil.trim(contract.getFileType()))) {
            return false;
        }
        if (Boolean.TRUE.equals(contract.getAmountNa())) {
            return false;
        }
        BigDecimal amount = contract.getContractAmount();
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }
}
