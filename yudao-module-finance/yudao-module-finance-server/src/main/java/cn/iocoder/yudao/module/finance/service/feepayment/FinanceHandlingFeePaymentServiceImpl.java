package cn.iocoder.yudao.module.finance.service.feepayment;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
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
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.HANDLING_FEE_PAYMENT_AMOUNT_INVALID;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.HANDLING_FEE_PAYMENT_IMPORT_EMPTY;
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
        if (CollUtil.isEmpty(rows)) {
            throw exception(HANDLING_FEE_PAYMENT_IMPORT_EMPTY);
        }
        List<DeptRespDTO> companies = entityCompanyResolver.loadEnabledCompanies();
        Map<Integer, String> failure = new LinkedHashMap<>();
        List<Long> created = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            int excelRow = i + 2;
            try {
                FinanceHandlingFeePaymentImportExcelVO row = rows.get(i);
                if (row.getFeeDate() == null) {
                    failure.put(excelRow, "付款日期不能为空");
                    continue;
                }
                if (row.getAmount() == null || row.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    failure.put(excelRow, "手续费金额必须大于 0");
                    continue;
                }
                ResolvedCompany[] out = new ResolvedCompany[1];
                String err = entityCompanyResolver.matchByNameOrError(row.getEntityCompanyName(), out, companies);
                if (err != null) {
                    failure.put(excelRow, err);
                    continue;
                }
                if (StrUtil.isBlank(row.getAccountNo())) {
                    failure.put(excelRow, "银行账号不能为空");
                    continue;
                }
                String accountNo = row.getAccountNo().trim();
                FinanceCompanyBankAccountDO account = bankAccountService.listEnabledByEntityCompany(out[0].deptId())
                        .stream().filter(a -> accountNo.equals(StrUtil.trim(a.getAccountNo())))
                        .findFirst().orElse(null);
                if (account == null) {
                    failure.put(excelRow, "银行账号不存在或不属于该主体公司启用账户");
                    continue;
                }
                String currency = StrUtil.isBlank(row.getCurrency())
                        ? StrUtil.blankToDefault(account.getCurrency(), "CNY")
                        : row.getCurrency();
                FinanceHandlingFeePaymentSaveReqVO req = new FinanceHandlingFeePaymentSaveReqVO();
                req.setFeeDate(row.getFeeDate());
                req.setAmount(row.getAmount());
                req.setCurrency(currency);
                req.setEntityCompanyDeptId(out[0].deptId());
                req.setCompanyBankAccountId(account.getId());
                created.add(create(req));
            } catch (ServiceException ex) {
                failure.put(excelRow, ex.getMessage());
            }
        }
        return FinanceHandlingFeePaymentImportRespVO.builder().createdIds(created).failureRows(failure).build();
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
                .accountName(account.getAccountHolder())
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
