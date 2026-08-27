package cn.iocoder.yudao.module.finance.service.feepayment;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.finance.controller.admin.feepayment.vo.FinanceHandlingFeePaymentImportExcelVO;
import cn.iocoder.yudao.module.finance.controller.admin.feepayment.vo.FinanceHandlingFeePaymentImportRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.feepayment.vo.FinanceHandlingFeePaymentPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.feepayment.vo.FinanceHandlingFeePaymentSaveReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.companyaccount.FinanceCompanyBankAccountDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.feepayment.FinanceHandlingFeePaymentDO;
import cn.iocoder.yudao.module.finance.dal.mysql.feepayment.FinanceHandlingFeePaymentMapper;
import cn.iocoder.yudao.module.finance.service.common.FinanceCurrencySupport;
import cn.iocoder.yudao.module.finance.service.common.FinanceEntityCompanyResolver;
import cn.iocoder.yudao.module.finance.service.common.FinanceEntityCompanyResolver.ResolvedCompany;
import cn.iocoder.yudao.module.finance.service.companyaccount.FinanceCompanyBankAccountService;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.HANDLING_FEE_PAYMENT_AMOUNT_INVALID;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.HANDLING_FEE_PAYMENT_NOT_EXISTS;

@Service
@Validated
public class FinanceHandlingFeePaymentServiceImpl implements FinanceHandlingFeePaymentService {

    private final FinanceHandlingFeePaymentMapper mapper;
    private final FinanceEntityCompanyResolver entityCompanyResolver;
    private final FinanceCompanyBankAccountService bankAccountService;

    public FinanceHandlingFeePaymentServiceImpl(FinanceHandlingFeePaymentMapper mapper,
                                                FinanceEntityCompanyResolver entityCompanyResolver,
                                                FinanceCompanyBankAccountService bankAccountService) {
        this.mapper = mapper;
        this.entityCompanyResolver = entityCompanyResolver;
        this.bankAccountService = bankAccountService;
    }

    @Override
    public Long create(FinanceHandlingFeePaymentSaveReqVO reqVO) {
        FinanceHandlingFeePaymentDO row = buildRow(reqVO);
        mapper.insert(row);
        return row.getId();
    }

    @Override
    public void update(FinanceHandlingFeePaymentSaveReqVO reqVO) {
        validateExists(reqVO.getId());
        FinanceHandlingFeePaymentDO row = buildRow(reqVO);
        row.setId(reqVO.getId());
        mapper.updateById(row);
    }

    @Override
    public void delete(Long id) {
        validateExists(id);
        mapper.deleteById(id);
    }

    @Override
    public FinanceHandlingFeePaymentDO get(Long id) {
        return validateExists(id);
    }

    @Override
    public PageResult<FinanceHandlingFeePaymentDO> getPage(FinanceHandlingFeePaymentPageReqVO reqVO) {
        return mapper.selectPage(reqVO);
    }

    @Override
    public FinanceHandlingFeePaymentImportRespVO importExcel(List<FinanceHandlingFeePaymentImportExcelVO> rows) {
        throw new UnsupportedOperationException();
    }

    private FinanceHandlingFeePaymentDO buildRow(FinanceHandlingFeePaymentSaveReqVO reqVO) {
        ResolvedCompany company = entityCompanyResolver.requireByDeptId(reqVO.getEntityCompanyDeptId());
        FinanceCompanyBankAccountDO account = bankAccountService
                .requireEnabledForEntityCompany(reqVO.getCompanyBankAccountId(), company.deptId());
        if (reqVO.getAmount() == null || reqVO.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw exception(HANDLING_FEE_PAYMENT_AMOUNT_INVALID);
        }
        String currency = FinanceCurrencySupport.requireSupported(reqVO.getCurrency());
        return FinanceHandlingFeePaymentDO.builder()
                .feeDate(reqVO.getFeeDate())
                .amount(reqVO.getAmount().setScale(2, RoundingMode.HALF_UP))
                .currency(currency)
                .entityCompanyDeptId(company.deptId())
                .entityCompanyName(company.name())
                .companyBankAccountId(account.getId())
                .accountName(account.getAccountName())
                .bankName(account.getBankName())
                .accountNo(account.getAccountNo())
                .accountNoMasked(FinanceCompanyBankAccountService.maskAccountNo(account.getAccountNo()))
                .build();
    }

    private FinanceHandlingFeePaymentDO validateExists(Long id) {
        FinanceHandlingFeePaymentDO row = mapper.selectById(id);
        if (row == null) {
            throw exception(HANDLING_FEE_PAYMENT_NOT_EXISTS);
        }
        return row;
    }

}
