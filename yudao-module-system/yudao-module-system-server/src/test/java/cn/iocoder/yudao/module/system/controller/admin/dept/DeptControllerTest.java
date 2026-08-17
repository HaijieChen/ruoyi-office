package cn.iocoder.yudao.module.system.controller.admin.dept;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptImportRespVO;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptListReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.service.dept.DeptImportService;
import cn.iocoder.yudao.module.system.service.dept.DeptService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @Test
    void importEndpointsExposeExpectedPathsAndDelegate() throws Exception {
        Method template = DeptController.class.getMethod("getImportTemplate",
                jakarta.servlet.http.HttpServletResponse.class);
        assertArrayEquals(new String[]{"/get-import-template"}, template.getAnnotation(GetMapping.class).value());
        assertTrue(template.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class)
                .value().contains("system:dept:import"));

        Method validate = DeptController.class.getMethod("validateImportDept", MultipartFile.class);
        assertArrayEquals(new String[]{"/import/validate"}, validate.getAnnotation(PostMapping.class).value());

        Method importMethod = DeptController.class.getMethod("importDept", MultipartFile.class, String.class);
        assertArrayEquals(new String[]{"/import"}, importMethod.getAnnotation(PostMapping.class).value());

        DeptImportService importService = mock(DeptImportService.class);
        when(importService.validateImport(any())).thenReturn(DeptImportRespVO.builder()
                .fileDigest("abc").totalRows(1).createCount(1).skipCount(0).canCommit(true).build());
        when(importService.importDepts(any(), eq("abc"))).thenReturn(DeptImportRespVO.builder()
                .fileDigest("abc").totalRows(1).createCount(1).skipCount(0).canCommit(true).build());
        DeptController controller = new DeptController();
        ReflectionTestUtils.setField(controller, "deptImportService", importService);

        MockMultipartFile file = new MockMultipartFile("file", "a.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{'P', 'K'});
        assertEquals("abc", controller.validateImportDept(file).getData().getFileDigest());
        assertEquals(1, controller.importDept(file, "abc").getData().getCreateCount());
        verify(importService).validateImport(any());
        verify(importService).importDepts(any(), eq("abc"));
    }

}
