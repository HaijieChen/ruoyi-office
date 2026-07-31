package cn.iocoder.yudao.module.finance.service.customer;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.finance.controller.admin.customer.vo.FinanceCustomerCompanyPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.customer.vo.FinanceCustomerCompanySaveReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.customer.FinanceCustomerCompanyDO;
import cn.iocoder.yudao.module.finance.dal.mysql.customer.FinanceCustomerCompanyMapper;
import cn.iocoder.yudao.module.finance.dal.redis.no.FinanceCustomerCompanyNoRedisDAO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.*;

@Service
@Validated
public class FinanceCustomerCompanyServiceImpl implements FinanceCustomerCompanyService {

    private final FinanceCustomerCompanyMapper customerCompanyMapper;
    private final FinanceCustomerCompanyNoRedisDAO customerCompanyNoRedisDAO;

    public FinanceCustomerCompanyServiceImpl(FinanceCustomerCompanyMapper customerCompanyMapper,
                                             FinanceCustomerCompanyNoRedisDAO customerCompanyNoRedisDAO) {
        this.customerCompanyMapper = customerCompanyMapper;
        this.customerCompanyNoRedisDAO = customerCompanyNoRedisDAO;
    }

    @Override
    public Long createCustomerCompany(FinanceCustomerCompanySaveReqVO createReqVO) {
        String name = requireName(createReqVO.getName());
        String taxNo = requireTaxNo(createReqVO.getTaxNo());
        validateTaxNoUnique(taxNo, null);

        Integer status = createReqVO.getStatus() != null
                ? createReqVO.getStatus()
                : FinanceCustomerCompanyDO.STATUS_ENABLE;
        validateStatus(status);

        FinanceCustomerCompanyDO company = buildFromSave(createReqVO, name, taxNo);
        company.setCode(customerCompanyNoRedisDAO.generate(LocalDate.now()));
        company.setPartyType(FinanceCustomerCompanyDO.PARTY_TYPE_CUSTOMER);
        company.setStatus(status);
        try {
            customerCompanyMapper.insert(company);
        } catch (DuplicateKeyException ex) {
            // 并发下 select-then-insert 竞态：uk_tax_no_tenant_deleted / uk_code_tenant_deleted
            throw mapDuplicateKey(ex);
        }
        return company.getId();
    }

    @Override
    public void updateCustomerCompany(FinanceCustomerCompanySaveReqVO updateReqVO) {
        if (updateReqVO.getId() == null) {
            throw exception(CUSTOMER_COMPANY_NOT_EXISTS);
        }
        FinanceCustomerCompanyDO existing = validateExists(updateReqVO.getId());
        String name = requireName(updateReqVO.getName());
        String taxNo = requireTaxNo(updateReqVO.getTaxNo());
        validateTaxNoUnique(taxNo, existing.getId());

        Integer status = updateReqVO.getStatus() != null ? updateReqVO.getStatus() : existing.getStatus();
        validateStatus(status);

        FinanceCustomerCompanyDO update = buildFromSave(updateReqVO, name, taxNo);
        update.setId(existing.getId());
        update.setCode(existing.getCode());
        update.setPartyType(FinanceCustomerCompanyDO.PARTY_TYPE_CUSTOMER);
        update.setStatus(status);
        try {
            customerCompanyMapper.updateById(update);
        } catch (DuplicateKeyException ex) {
            throw mapDuplicateKey(ex);
        }
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        validateExists(id);
        validateStatus(status);
        if (status == FinanceCustomerCompanyDO.STATUS_ENABLE) {
            FinanceCustomerCompanyDO current = customerCompanyMapper.selectById(id);
            requireName(current.getName());
            requireTaxNo(current.getTaxNo());
        }
        FinanceCustomerCompanyDO update = new FinanceCustomerCompanyDO();
        update.setId(id);
        update.setStatus(status);
        customerCompanyMapper.updateById(update);
    }

    @Override
    public FinanceCustomerCompanyDO getCustomerCompany(Long id) {
        return customerCompanyMapper.selectById(id);
    }

    @Override
    public FinanceCustomerCompanyDO getEnabledCustomerCompany(Long id) {
        if (id == null) {
            throw exception(INVOICE_APPLICATION_CUSTOMER_COMPANY_REQUIRED);
        }
        FinanceCustomerCompanyDO company = customerCompanyMapper.selectById(id);
        if (company == null) {
            throw exception(CUSTOMER_COMPANY_NOT_EXISTS);
        }
        if (!Objects.equals(company.getStatus(), FinanceCustomerCompanyDO.STATUS_ENABLE)) {
            throw exception(INVOICE_APPLICATION_CUSTOMER_COMPANY_DISABLED);
        }
        return company;
    }

    @Override
    public PageResult<FinanceCustomerCompanyDO> getCustomerCompanyPage(FinanceCustomerCompanyPageReqVO pageReqVO) {
        return customerCompanyMapper.selectPage(pageReqVO);
    }

    @Override
    public List<FinanceCustomerCompanyDO> getEnabledSimpleList() {
        return customerCompanyMapper.selectEnabledList();
    }

    private FinanceCustomerCompanyDO validateExists(Long id) {
        FinanceCustomerCompanyDO company = customerCompanyMapper.selectById(id);
        if (company == null) {
            throw exception(CUSTOMER_COMPANY_NOT_EXISTS);
        }
        return company;
    }

    private void validateTaxNoUnique(String taxNo, Long excludeId) {
        FinanceCustomerCompanyDO existing = customerCompanyMapper.selectByTaxNo(taxNo);
        if (existing != null && (excludeId == null || !Objects.equals(existing.getId(), excludeId))) {
            throw exception(CUSTOMER_COMPANY_TAX_NO_EXISTS);
        }
    }

    /**
     * 将 DB 唯一约束冲突映射为业务错误；税号 uk 为常见竞态路径。
     */
    private static RuntimeException mapDuplicateKey(DuplicateKeyException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "";
        String lower = msg.toLowerCase();
        // 税号唯一索引名或列名命中 → 税号冲突
        if (lower.contains("tax_no") || lower.contains("uk_tax_no")) {
            return exception(CUSTOMER_COMPANY_TAX_NO_EXISTS);
        }
        // 编码唯一冲突（极低概率：同秒 Redis 序号异常）也按税号业务语义对外统一，避免泄漏 SQL
        // 若 message 无列信息，默认税号冲突（并发建档最常见）
        return exception(CUSTOMER_COMPANY_TAX_NO_EXISTS);
    }

    private static void validateStatus(Integer status) {
        if (status == null
                || (status != FinanceCustomerCompanyDO.STATUS_ENABLE
                && status != FinanceCustomerCompanyDO.STATUS_DISABLE)) {
            throw exception(CUSTOMER_COMPANY_STATUS_INVALID);
        }
    }

    private static String requireName(String name) {
        if (StrUtil.isBlank(name)) {
            throw exception(CUSTOMER_COMPANY_NAME_REQUIRED);
        }
        return name.trim();
    }

    private static String requireTaxNo(String taxNo) {
        if (StrUtil.isBlank(taxNo)) {
            throw exception(CUSTOMER_COMPANY_TAX_NO_REQUIRED);
        }
        return taxNo.trim();
    }

    private static FinanceCustomerCompanyDO buildFromSave(FinanceCustomerCompanySaveReqVO reqVO,
                                                          String name, String taxNo) {
        return FinanceCustomerCompanyDO.builder()
                .name(name)
                .taxNo(taxNo)
                .bankName(trimToNull(reqVO.getBankName()))
                .bankAccount(trimToNull(reqVO.getBankAccount()))
                .address(trimToNull(reqVO.getAddress()))
                .phone(trimToNull(reqVO.getPhone()))
                .contactName(trimToNull(reqVO.getContactName()))
                .email(trimToNull(reqVO.getEmail()))
                .build();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

}
