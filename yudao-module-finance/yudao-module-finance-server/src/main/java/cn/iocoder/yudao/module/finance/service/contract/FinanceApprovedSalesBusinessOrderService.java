package cn.iocoder.yudao.module.finance.service.contract;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderSaveReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.business.FinanceBusinessOrderDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.contract.FinanceContractApplicationDO;
import cn.iocoder.yudao.module.finance.dal.mysql.business.FinanceBusinessOrderMapper;
import cn.iocoder.yudao.module.finance.service.business.FinanceBusinessOrderService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class FinanceApprovedSalesBusinessOrderService {

    private static final Logger log = LoggerFactory.getLogger(FinanceApprovedSalesBusinessOrderService.class);

    private final FinanceBusinessOrderMapper businessOrderMapper;
    private final FinanceBusinessOrderService businessOrderService;
    private final AdminUserApi adminUserApi;

    public FinanceApprovedSalesBusinessOrderService(FinanceBusinessOrderMapper businessOrderMapper,
                                                    FinanceBusinessOrderService businessOrderService,
                                                    AdminUserApi adminUserApi) {
        this.businessOrderMapper = businessOrderMapper;
        this.businessOrderService = businessOrderService;
        this.adminUserApi = adminUserApi;
    }

    /** @return 新商务单 id；跳过或失败返回 null */
    public Long createIfEligible(FinanceContractApplicationDO contract) {
        if (contract == null || contract.getId() == null) {
            return null;
        }
        long existing = businessOrderMapper.selectCount(new LambdaQueryWrapper<FinanceBusinessOrderDO>()
                .eq(FinanceBusinessOrderDO::getContractApplicationId, contract.getId()));
        if (!FinanceApprovedSalesBusinessOrderSupport.shouldCreate(contract, existing)) {
            return null;
        }
        if (contract.getEntityCompanyDeptId() == null || contract.getApplicantUserId() == null) {
            log.warn("[auto-bo] skip contract {} missing entity/applicant", contract.getId());
            return null;
        }
        FinanceBusinessOrderSaveReqVO req = new FinanceBusinessOrderSaveReqVO();
        req.setEntityCompanyDeptId(contract.getEntityCompanyDeptId());
        req.setContractApplicationId(contract.getId());
        LocalDate today = LocalDate.now();
        req.setOrderDate(today);
        req.setContactPerson(resolveContactPerson(contract.getApplicantUserId()));
        req.setExecutionStartDate(contract.getStartDate() != null ? contract.getStartDate() : today);
        req.setExecutionEndDate(contract.getEndDate() != null ? contract.getEndDate() : req.getExecutionStartDate());
        req.setPayerName(contract.getCounterpartyName());
        req.setSignedExecutionAmount(contract.getContractAmount());
        req.setDiscountRate(BigDecimal.ZERO);
        req.setCurrency(StrUtil.blankToDefault(contract.getCurrency(), "CNY"));
        req.setRemark("销售合同审批通过自动生成");
        return businessOrderService.createBusinessOrder(req, contract.getApplicantUserId());
    }

    private String resolveContactPerson(Long userId) {
        try {
            CommonResult<AdminUserRespDTO> res = adminUserApi.getUser(userId);
            AdminUserRespDTO user = res == null ? null : res.getCheckedData();
            if (user != null && StrUtil.isNotBlank(user.getNickname())) {
                return user.getNickname();
            }
        } catch (Exception ex) {
            log.warn("[auto-bo] resolve user {} failed: {}", userId, ex.toString());
        }
        return String.valueOf(userId);
    }
}
