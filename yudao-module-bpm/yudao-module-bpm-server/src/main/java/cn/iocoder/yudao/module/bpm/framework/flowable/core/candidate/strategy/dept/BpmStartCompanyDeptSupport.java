package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.dept;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.number.NumberUtils;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按发起任职公司，从勾选部门中筛出属于该公司的部门。
 */
final class BpmStartCompanyDeptSupport {

    private static final String ORG_TYPE_COMPANY = "1";

    private BpmStartCompanyDeptSupport() {
    }

    static Long resolveStartCompanyDeptId(Map<String, Object> processVariables,
                                          Long startUserId,
                                          DeptApi deptApi,
                                          AdminUserApi adminUserApi) {
        if (processVariables != null) {
            for (String key : List.of("startCompanyDeptId", "entityCompanyDeptId", "companyId")) {
                Object raw = processVariables.get(key);
                Long id = NumberUtils.parseLong(raw == null ? null : String.valueOf(raw));
                if (id != null && id > 0) {
                    return id;
                }
            }
            Object startDeptRaw = processVariables.get("startDeptId");
            Long startDeptId = NumberUtils.parseLong(
                    startDeptRaw == null ? null : String.valueOf(startDeptRaw));
            Long fromStartDept = findCompanyId(startDeptId, deptApi);
            if (fromStartDept != null) {
                return fromStartDept;
            }
        }
        if (startUserId == null) {
            return null;
        }
        AdminUserRespDTO user = adminUserApi.getUser(startUserId).getCheckedData();
        if (user == null || user.getDeptId() == null) {
            return null;
        }
        return findCompanyId(user.getDeptId(), deptApi);
    }

    static List<Long> filterDeptIdsUnderCompany(Set<Long> deptIds, Long companyDeptId, DeptApi deptApi) {
        List<Long> matched = new ArrayList<>();
        if (companyDeptId == null || deptIds == null) {
            return matched;
        }
        for (Long deptId : deptIds) {
            if (companyDeptId.equals(findCompanyId(deptId, deptApi))) {
                matched.add(deptId);
            }
        }
        return matched;
    }

    static Long findCompanyId(Long deptId, DeptApi deptApi) {
        if (deptId == null || deptId <= 0) {
            return null;
        }
        Long current = deptId;
        for (int i = 0; i < 16 && current != null && current > 0; i++) {
            DeptRespDTO dept = deptApi.getDept(current).getCheckedData();
            if (dept == null) {
                return null;
            }
            if (StrUtil.equals(ORG_TYPE_COMPANY, dept.getOrgType())) {
                return dept.getId();
            }
            current = dept.getParentId();
        }
        return null;
    }

    static Set<Long> leaderUserIds(List<DeptRespDTO> depts) {
        Set<Long> ids = new LinkedHashSet<>();
        if (depts == null) {
            return ids;
        }
        for (DeptRespDTO dept : depts) {
            if (dept != null && dept.getLeaderUserId() != null) {
                ids.add(dept.getLeaderUserId());
            }
        }
        return ids;
    }
}
