package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.other;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.bpm.api.task.BpmCompanyFinanceApproverProvider;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BpmTaskCandidateStartUserCompanyFinanceStrategyTest {

    private AdminUserApi adminUserApi;
    private DeptApi deptApi;
    private BpmCompanyFinanceApproverProvider provider;
    private BpmTaskCandidateStartUserCompanyFinanceStrategy strategy;

    @BeforeEach
    void setUp() {
        adminUserApi = mock(AdminUserApi.class);
        deptApi = mock(DeptApi.class);
        provider = mock(BpmCompanyFinanceApproverProvider.class);
        strategy = new BpmTaskCandidateStartUserCompanyFinanceStrategy();
        strategy.adminUserApi = adminUserApi;
        strategy.deptApi = deptApi;
        @SuppressWarnings("unchecked")
        ObjectProvider<BpmCompanyFinanceApproverProvider> op = mock(ObjectProvider.class);
        when(op.getIfAvailable()).thenReturn(provider);
        strategy.approverProvider = op;
    }

    @Test
    void resolveApproversWalksToCompany() {
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(8L);
        user.setDeptId(20L);
        when(adminUserApi.getUser(8L)).thenReturn(CommonResult.success(user));
        DeptRespDTO dept = new DeptRespDTO();
        dept.setId(20L);
        dept.setParentId(10L);
        dept.setOrgType("0");
        when(deptApi.getDept(20L)).thenReturn(CommonResult.success(dept));
        DeptRespDTO company = new DeptRespDTO();
        company.setId(10L);
        company.setParentId(0L);
        company.setOrgType("1");
        when(deptApi.getDept(10L)).thenReturn(CommonResult.success(company));
        when(provider.listUserIdsByCompanyDeptId(10L)).thenReturn(Set.of(88L, 99L));
        assertEquals(Set.of(88L, 99L), strategy.resolveApprovers(8L));
    }
}
