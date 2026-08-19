package cn.iocoder.yudao.module.finance.service.opening;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.finance.controller.admin.opening.vo.FinanceBankOpeningBalancePageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.opening.vo.FinanceBankOpeningBalanceSaveReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.companyaccount.FinanceCompanyBankAccountDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.opening.FinanceBankOpeningBalanceDO;
import cn.iocoder.yudao.module.finance.dal.mysql.opening.FinanceBankOpeningBalanceMapper;
import cn.iocoder.yudao.module.finance.service.common.FinanceCurrencySupport;
import cn.iocoder.yudao.module.finance.service.companyaccount.FinanceCompanyBankAccountService;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.COMPANY_BANK_ACCOUNT_NOT_EXISTS;

@Service
@Validated
public class FinanceBankOpeningBalanceServiceImpl implements FinanceBankOpeningBalanceService {

    private final FinanceBankOpeningBalanceMapper mapper;
    private final FinanceCompanyBankAccountService bankAccountService;

    public FinanceBankOpeningBalanceServiceImpl(FinanceBankOpeningBalanceMapper mapper,
                                                FinanceCompanyBankAccountService bankAccountService) {
        this.mapper = mapper;
        this.bankAccountService = bankAccountService;
    }

    @Override
    public Long upsert(FinanceBankOpeningBalanceSaveReqVO reqVO) {
        FinanceCompanyBankAccountDO account = bankAccountService.get(reqVO.getAccountId());
        if (account == null) {
            throw exception(COMPANY_BANK_ACCOUNT_NOT_EXISTS);
        }
        String currency = FinanceCurrencySupport.requireSupported(reqVO.getCurrency());
        FinanceBankOpeningBalanceDO existing = mapper.selectByAccountId(reqVO.getAccountId());
        if (existing == null) {
            FinanceBankOpeningBalanceDO row = FinanceBankOpeningBalanceDO.builder()
                    .accountId(reqVO.getAccountId())
                    .asOfDate(reqVO.getAsOfDate())
                    .amount(reqVO.getAmount())
                    .currency(currency)
                    .build();
            mapper.insert(row);
            return row.getId();
        }
        existing.setAsOfDate(reqVO.getAsOfDate());
        existing.setAmount(reqVO.getAmount());
        existing.setCurrency(currency);
        mapper.updateById(existing);
        return existing.getId();
    }

    @Override
    public FinanceBankOpeningBalanceDO getByAccountId(Long accountId) {
        return mapper.selectByAccountId(accountId);
    }

    @Override
    public PageResult<FinanceBankOpeningBalanceDO> getPage(FinanceBankOpeningBalancePageReqVO reqVO) {
        return mapper.selectPage(reqVO);
    }

}
