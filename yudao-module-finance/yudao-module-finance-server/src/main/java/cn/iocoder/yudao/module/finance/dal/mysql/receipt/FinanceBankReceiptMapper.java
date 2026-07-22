package cn.iocoder.yudao.module.finance.dal.mysql.receipt;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.MPJLambdaWrapperX;
import cn.iocoder.yudao.module.finance.controller.admin.receipt.vo.FinanceReceiptPageReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.receipt.FinanceReceiptDO;
import cn.iocoder.yudao.module.finance.enums.FinanceReceiptClaimStatusEnum;
import org.apache.ibatis.annotations.Mapper;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface FinanceBankReceiptMapper extends BaseMapperX<FinanceReceiptDO> {

    default PageResult<FinanceReceiptDO> selectUnclaimedPage(FinanceReceiptPageReqVO reqVO) {
        return selectPage(reqVO, new MPJLambdaWrapperX<FinanceReceiptDO>()
                .in(FinanceReceiptDO::getClaimStatus, List.of(FinanceReceiptClaimStatusEnum.UNCLAIMED.getStatus(),
                        FinanceReceiptClaimStatusEnum.PARTIALLY_CLAIMED.getStatus()))
                .gtIfPresent(FinanceReceiptDO::getUnclaimedAmount, BigDecimal.ZERO)
                .likeIfPresent(FinanceReceiptDO::getReceiptNo, reqVO.getReceiptNo())
                .likeIfPresent(FinanceReceiptDO::getBankAccount, reqVO.getBankAccount())
                .betweenIfPresent(FinanceReceiptDO::getTransactionDate, reqVO.getTransactionDate())
                .likeIfPresent(FinanceReceiptDO::getPayerName, reqVO.getPayerName())
                .likeIfPresent(FinanceReceiptDO::getPayerAccount, reqVO.getPayerAccount())
                .likeIfPresent(FinanceReceiptDO::getBankSerialNo, reqVO.getBankSerialNo())
                .betweenIfPresent(FinanceReceiptDO::getImportDate, reqVO.getImportDate())
                .orderByDesc(FinanceReceiptDO::getId));
    }

    default FinanceReceiptDO selectByReceiptNo(String receiptNo) {
        return selectOne("receipt_no", receiptNo);
    }

    default FinanceReceiptDO selectByBankSerialNo(String bankSerialNo) {
        return selectOne("bank_serial_no", bankSerialNo);
    }

}
