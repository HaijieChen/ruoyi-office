package cn.iocoder.yudao.module.system.service.dept;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptImportExcelVO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.enums.OrgTypeEnum;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeptExportSupportTest {

    @Test
    void exportRowsMatchImportTemplateAndParentBeforeChild() {
        DeptDO company = new DeptDO();
        company.setId(1L);
        company.setName("集团");
        company.setParentId(DeptDO.PARENT_ID_ROOT);
        company.setOrgType(OrgTypeEnum.COMPANY.getValue());
        company.setSort(1);
        company.setStatus(CommonStatusEnum.ENABLE.getStatus());
        company.setFunctionalCurrency("CNY");
        company.setLeaderUserId(9L);
        company.setPhone("13800000000");
        company.setEmail("a@b.com");

        DeptDO dept = new DeptDO();
        dept.setId(2L);
        dept.setName("研发");
        dept.setParentId(1L);
        dept.setOrgType(OrgTypeEnum.DEPARTMENT.getValue());
        dept.setSort(2);
        dept.setStatus(CommonStatusEnum.DISABLE.getStatus());

        AdminUserDO leader = new AdminUserDO();
        leader.setId(9L);
        leader.setUsername("leader1");

        List<DeptImportExcelVO> rows = DeptImportSupport.toExportRows(
                List.of(dept, company), Map.of(9L, leader));

        assertEquals(2, rows.size());
        assertEquals("集团", rows.get(0).getName());
        assertEquals("", rows.get(0).getParentPath());
        assertEquals("公司", rows.get(0).getOrgTypeLabel());
        assertEquals("1", rows.get(0).getSortText());
        assertEquals("启用", rows.get(0).getStatusLabel());
        assertEquals("CNY", rows.get(0).getFunctionalCurrency());
        assertEquals("leader1", rows.get(0).getLeaderUsername());
        assertEquals("13800000000", rows.get(0).getPhone());
        assertEquals("a@b.com", rows.get(0).getEmail());

        assertEquals("研发", rows.get(1).getName());
        assertEquals("集团", rows.get(1).getParentPath());
        assertEquals("部门", rows.get(1).getOrgTypeLabel());
        assertEquals("停用", rows.get(1).getStatusLabel());
        assertEquals("", rows.get(1).getLeaderUsername() == null ? "" : rows.get(1).getLeaderUsername());
    }
}
