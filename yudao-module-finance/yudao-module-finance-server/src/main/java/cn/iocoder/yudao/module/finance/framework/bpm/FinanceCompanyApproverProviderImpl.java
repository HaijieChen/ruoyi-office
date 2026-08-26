package cn.iocoder.yudao.module.finance.framework.bpm;

import cn.iocoder.yudao.module.bpm.api.task.BpmCompanyFinanceApproverProvider;
import cn.iocoder.yudao.module.finance.service.approver.FinanceCompanyApproverService;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class FinanceCompanyApproverProviderImpl implements BpmCompanyFinanceApproverProvider {

    private final FinanceCompanyApproverService companyApproverService;

    public FinanceCompanyApproverProviderImpl(FinanceCompanyApproverService companyApproverService) {
        this.companyApproverService = companyApproverService;
    }

    @Override
    public Set<Long> listUserIdsByCompanyDeptId(Long companyDeptId) {
        return companyApproverService.listUserIdsByCompany(companyDeptId);
    }
}
