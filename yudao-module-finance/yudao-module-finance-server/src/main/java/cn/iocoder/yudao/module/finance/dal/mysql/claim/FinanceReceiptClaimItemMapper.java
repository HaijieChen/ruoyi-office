package cn.iocoder.yudao.module.finance.dal.mysql.claim;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.finance.dal.dataobject.claim.FinanceReceiptClaimItemDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface FinanceReceiptClaimItemMapper extends BaseMapperX<FinanceReceiptClaimItemDO> {

    default List<FinanceReceiptClaimItemDO> selectListByClaimId(Long claimId) {
        return selectList(FinanceReceiptClaimItemDO::getClaimId, claimId);
    }

    default int deleteByClaimId(Long claimId) {
        return delete(FinanceReceiptClaimItemDO::getClaimId, claimId);
    }

}
