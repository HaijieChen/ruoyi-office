package cn.iocoder.yudao.module.system.controller.admin.dept;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptListReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.service.dept.DeptService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeptControllerTest {

    @Test
    void getSimpleCompanyListOnlyRequestsEnabledCompanies() {
        DeptService deptService = mock(DeptService.class);
        when(deptService.getCompanyList(any())).thenReturn(List.of(new DeptDO().setId(1L).setName("A公司")));
        DeptController controller = new DeptController();
        ReflectionTestUtils.setField(controller, "deptService", deptService);

        var result = controller.getSimpleCompanyList();

        ArgumentCaptor<DeptListReqVO> request = ArgumentCaptor.forClass(DeptListReqVO.class);
        verify(deptService).getCompanyList(request.capture());
        assertEquals(CommonStatusEnum.ENABLE.getStatus(), request.getValue().getStatus());
        assertEquals(1L, result.getData().get(0).getId());
    }

}
