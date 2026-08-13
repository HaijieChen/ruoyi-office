package cn.iocoder.yudao.module.finance.service.companyaccount;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.finance.controller.admin.companyaccount.vo.FinanceCompanyBankAccountPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.companyaccount.vo.FinanceCompanyBankAccountSaveReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.companyaccount.FinanceCompanyBankAccountDO;
import cn.iocoder.yudao.module.finance.dal.mysql.companyaccount.FinanceCompanyBankAccountMapper;
import cn.iocoder.yudao.module.finance.service.common.FinanceCurrencySupport;
import cn.iocoder.yudao.module.finance.service.common.FinanceEntityCompanyResolver;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.*;

@Service
@Validated
public class FinanceCompanyBankAccountServiceImpl implements FinanceCompanyBankAccountService {

    private final FinanceCompanyBankAccountMapper mapper;
    private final FinanceEntityCompanyResolver entityCompanyResolver;

    public FinanceCompanyBankAccountServiceImpl(FinanceCompanyBankAccountMapper mapper,
                                                FinanceEntityCompanyResolver entityCompanyResolver) {
        this.mapper = mapper;
        this.entityCompanyResolver = entityCompanyResolver;
    }

    @Override
    public Long create(FinanceCompanyBankAccountSaveReqVO reqVO) {
        entityCompanyResolver.requireByDeptId(reqVO.getEntityCompanyDeptId());
        FinanceCompanyBankAccountDO row = buildFromSave(reqVO);
        validateAccountNoUnique(row.getEntityCompanyDeptId(), row.getAccountNo(), null);
        mapper.insert(row);
        return row.getId();
    }

    @Override
    public void update(FinanceCompanyBankAccountSaveReqVO reqVO) {
        if (reqVO.getId() == null) {
            throw exception(COMPANY_BANK_ACCOUNT_NOT_EXISTS);
        }
        FinanceCompanyBankAccountDO existing = validateExists(reqVO.getId());
        entityCompanyResolver.requireByDeptId(reqVO.getEntityCompanyDeptId());
        String accountNo = requireText(reqVO.getAccountNo(), "银行账号不能为空");
        validateAccountNoUnique(reqVO.getEntityCompanyDeptId(), accountNo, existing.getId());
        Integer status = reqVO.getStatus() != null ? reqVO.getStatus() : existing.getStatus();
        validateStatus(status);
        mapper.update(null, new UpdateWrapper<FinanceCompanyBankAccountDO>()
                .eq("id", existing.getId())
                .set("entity_company_dept_id", reqVO.getEntityCompanyDeptId())
                .set("account_name", requireText(reqVO.getAccountName(), "账户名称不能为空"))
                .set("bank_name", requireText(reqVO.getBankName(), "开户行不能为空"))
                .set("account_holder", requireText(reqVO.getAccountHolder(), "户名不能为空"))
                .set("account_no", accountNo)
                .set("account_type", trimToNull(reqVO.getAccountType()))
                .set("currency", FinanceCurrencySupport.requireSupported(reqVO.getCurrency()))
                .set("status", status)
                .set("remark", trimToNull(reqVO.getRemark())));
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        validateExists(id);
        validateStatus(status);
        FinanceCompanyBankAccountDO update = new FinanceCompanyBankAccountDO();
        update.setId(id);
        update.setStatus(status);
        mapper.updateById(update);
    }

    @Override
    public FinanceCompanyBankAccountDO get(Long id) {
        return mapper.selectById(id);
    }

    @Override
    public FinanceCompanyBankAccountDO requireEnabledForEntityCompany(Long accountId, Long entityCompanyDeptId) {
        if (accountId == null) {
            throw exception(COMPANY_BANK_ACCOUNT_REQUIRED);
        }
        FinanceCompanyBankAccountDO account = mapper.selectById(accountId);
        if (account == null) {
            throw exception(COMPANY_BANK_ACCOUNT_NOT_EXISTS);
        }
        if (!Objects.equals(FinanceCompanyBankAccountDO.STATUS_ENABLE, account.getStatus())) {
            throw exception(COMPANY_BANK_ACCOUNT_DISABLED);
        }
        if (entityCompanyDeptId == null
                || !Objects.equals(entityCompanyDeptId, account.getEntityCompanyDeptId())) {
            throw exception(COMPANY_BANK_ACCOUNT_ENTITY_MISMATCH);
        }
        return account;
    }

    @Override
    public PageResult<FinanceCompanyBankAccountDO> getPage(FinanceCompanyBankAccountPageReqVO reqVO) {
        return mapper.selectPage(reqVO);
    }

    @Override
    public List<FinanceCompanyBankAccountDO> listEnabledByEntityCompany(Long entityCompanyDeptId) {
        if (entityCompanyDeptId == null) {
            throw exception(ENTITY_COMPANY_REQUIRED);
        }
        // 校验主体存在且为启用公司（不双写主体字段）
        entityCompanyResolver.requireByDeptId(entityCompanyDeptId);
        return mapper.selectEnabledByEntityCompany(entityCompanyDeptId);
    }

    private FinanceCompanyBankAccountDO buildFromSave(FinanceCompanyBankAccountSaveReqVO reqVO) {
        Integer status = reqVO.getStatus() != null
                ? reqVO.getStatus()
                : FinanceCompanyBankAccountDO.STATUS_ENABLE;
        validateStatus(status);
        return FinanceCompanyBankAccountDO.builder()
                .entityCompanyDeptId(reqVO.getEntityCompanyDeptId())
                .accountName(requireText(reqVO.getAccountName(), "账户名称不能为空"))
                .bankName(requireText(reqVO.getBankName(), "开户行不能为空"))
                .accountHolder(requireText(reqVO.getAccountHolder(), "户名不能为空"))
                .accountNo(requireText(reqVO.getAccountNo(), "银行账号不能为空"))
                .accountType(trimToNull(reqVO.getAccountType()))
                .currency(FinanceCurrencySupport.requireSupported(reqVO.getCurrency()))
                .status(status)
                .remark(trimToNull(reqVO.getRemark()))
                .build();
    }

    private void validateAccountNoUnique(Long entityCompanyDeptId, String accountNo, Long excludeId) {
        FinanceCompanyBankAccountDO other = mapper.selectByCompanyAndAccountNo(entityCompanyDeptId, accountNo);
        if (other != null && (excludeId == null || !Objects.equals(other.getId(), excludeId))) {
            throw exception(COMPANY_BANK_ACCOUNT_NO_DUPLICATE);
        }
    }

    private FinanceCompanyBankAccountDO validateExists(Long id) {
        FinanceCompanyBankAccountDO row = mapper.selectById(id);
        if (row == null) {
            throw exception(COMPANY_BANK_ACCOUNT_NOT_EXISTS);
        }
        return row;
    }

    private static void validateStatus(Integer status) {
        if (status == null
                || (status != FinanceCompanyBankAccountDO.STATUS_ENABLE
                && status != FinanceCompanyBankAccountDO.STATUS_DISABLE)) {
            throw exception(COMPANY_BANK_ACCOUNT_STATUS_INVALID);
        }
    }

    private static String requireText(String value, String ignoredMessage) {
        if (StrUtil.isBlank(value)) {
            throw exception(COMPANY_BANK_ACCOUNT_FIELD_REQUIRED);
        }
        return value.trim();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

}
