package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.dept;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
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

public class BpmTaskCandidateStartUserCompanyDeptLeaderStrategyTest extends BaseMockitoUnitTest {

    @InjectMocks
    private BpmTaskCandidateStartUserCompanyDeptLeaderStrategy strategy;

    @Mock
    private DeptApi deptApi;
    @Mock
    private AdminUserApi adminUserApi;

    @Test
    public void resolve_keepsLeadersOnlyUnderStartCompany() {
        DeptRespDTO companyA = company(100L, "文枢");
        DeptRespDTO companyB = company(200L, "另一家");
        DeptRespDTO financeA = dept(11L, 100L, 111L);
        DeptRespDTO financeB = dept(12L, 200L, 222L);
        when(deptApi.getDept(11L)).thenReturn(success(financeA));
        when(deptApi.getDept(12L)).thenReturn(success(financeB));
        when(deptApi.getDept(100L)).thenReturn(success(companyA));
        when(deptApi.getDept(200L)).thenReturn(success(companyB));
        when(deptApi.getDeptList(any())).thenReturn(success(List.of(financeA)));

        Set<Long> users = strategy.resolve(
                "11,12", 1L, Map.of("startCompanyDeptId", 100L));
        assertEquals(Set.of(111L), users);
    }

    private static DeptRespDTO company(Long id, String name) {
        DeptRespDTO d = new DeptRespDTO();
        d.setId(id);
        d.setName(name);
        d.setOrgType("1");
        d.setParentId(0L);
        return d;
    }

    private static DeptRespDTO dept(Long id, Long parentId, Long leaderUserId) {
        DeptRespDTO d = new DeptRespDTO();
        d.setId(id);
        d.setParentId(parentId);
        d.setOrgType("0");
        d.setLeaderUserId(leaderUserId);
        return d;
    }
}
