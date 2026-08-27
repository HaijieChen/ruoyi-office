package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.user;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class BpmTaskCandidateStartUserCompanyPostStrategyTest extends BaseMockitoUnitTest {

    @InjectMocks
    private BpmTaskCandidateStartUserCompanyPostStrategy strategy;

    @Mock
    private AdminUserApi adminUserApi;
    @Mock
    private DeptApi deptApi;

    @Test
    public void resolve_keepsPostUsersOnlyUnderStartCompany() {
        AdminUserRespDTO inCompany = new AdminUserRespDTO().setId(11L).setDeptId(101L);
        AdminUserRespDTO otherCompany = new AdminUserRespDTO().setId(22L).setDeptId(201L);
        when(adminUserApi.getUserListByPostIds(any())).thenReturn(success(List.of(inCompany, otherCompany)));

        DeptRespDTO deptA = new DeptRespDTO();
        deptA.setId(101L);
        deptA.setParentId(100L);
        deptA.setOrgType("0");
        DeptRespDTO companyA = new DeptRespDTO();
        companyA.setId(100L);
        companyA.setOrgType("1");
        companyA.setParentId(0L);
        DeptRespDTO deptB = new DeptRespDTO();
        deptB.setId(201L);
        deptB.setParentId(200L);
        deptB.setOrgType("0");
        DeptRespDTO companyB = new DeptRespDTO();
        companyB.setId(200L);
        companyB.setOrgType("1");
        companyB.setParentId(0L);
        when(deptApi.getDept(101L)).thenReturn(success(deptA));
        when(deptApi.getDept(100L)).thenReturn(success(companyA));
        when(deptApi.getDept(201L)).thenReturn(success(deptB));
        when(deptApi.getDept(200L)).thenReturn(success(companyB));

        Set<Long> users = strategy.resolve("1", 9L, Map.of("startCompanyDeptId", 100L));
        assertEquals(Set.of(11L), users);
    }
}
