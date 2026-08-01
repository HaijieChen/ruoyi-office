package cn.iocoder.yudao.module.finance.service.business;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.finance.controller.admin.business.FinanceBusinessOrderController;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderImportExcelVO;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderSaveReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.business.FinanceBusinessOrderDO;
import cn.iocoder.yudao.module.finance.service.business.FinanceBusinessOrderService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.Test;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.util.Set;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FinanceBusinessOrderControllerContractTest {

    @Test
    void importTemplateEndpointShouldUseImportPermission() throws NoSuchMethodException {
        Method method = FinanceBusinessOrderController.class.getDeclaredMethod(
                "importTemplate", HttpServletResponse.class);

        assertArrayEquals(new String[]{"/get-import-template"}, method.getAnnotation(GetMapping.class).value());
        assertEquals("@ss.hasPermission('finance:business-order:import')",
                method.getAnnotation(PreAuthorize.class).value());
    }

    @Test
    void importEndpointShouldRequireFileOnlyAndImportPermission() throws NoSuchMethodException {
        Method method = FinanceBusinessOrderController.class.getDeclaredMethod(
                "importBusinessOrder", MultipartFile.class);

        PostMapping postMapping = method.getAnnotation(PostMapping.class);
        assertNotNull(postMapping);
        assertArrayEquals(new String[]{"/import"}, postMapping.value());
        assertEquals("@ss.hasPermission('finance:business-order:import')",
                method.getAnnotation(PreAuthorize.class).value());

        Parameter[] parameters = method.getParameters();
        assertEquals(1, parameters.length);
        RequestParam fileParam = parameters[0].getAnnotation(RequestParam.class);
        assertEquals("file", fileParam.value());
        assertTrue(fileParam.required());
    }

    @Test
    void saveRequestShouldExposeOnlyClientWritableSheetFields() {
        assertEquals(Set.of("id", "entityCompanyDeptId", "contractProcessId", "contractApplicationId", "orderDate", "productName",
                        "contactPerson", "executionStartDate", "executionEndDate", "payerName", "signedExecutionAmount",
                        "discountRate", "remark"),
                declaredFields(FinanceBusinessOrderSaveReqVO.class));
    }

    @Test
    void importRowShouldNotAcceptAuthoritativeSettlement() {
        assertEquals(Set.of("entityCompanyName", "contractProcessId", "contractApplicationNo", "orderDate", "productName", "contactPerson",
                        "executionStartDate", "executionEndDate", "payerName", "signedExecutionAmount", "discountRate",
                        "summary"),
                declaredFields(FinanceBusinessOrderImportExcelVO.class));
    }

    @Test
    void responseAndDomainShouldExposeSheetAndGeneratedFieldsWithoutGenericFields() {
        Set<String> requiredFields = Set.of("orderNo", "importDate", "importerId", "contractProcessId",
                "contractApplicationId", "orderDate",
                "productName", "contactPerson", "executionStartDate", "executionEndDate", "payerName",
                "signedExecutionAmount", "discountRate", "settlementAmount", "entityCompanyDeptId", "entityCompanyName", "remark",
                "confirmedClaimedAmount", "sourceRowHash");
        Set<String> genericFields = Set.of("businessSubject", "businessType", "contractRef", "projectRef",
                "receivableAmount", "payableAmount", "ownerId", "status", "currency", "bankAccount");

        assertTrue(declaredFields(FinanceBusinessOrderRespVO.class).containsAll(requiredFields));
        assertTrue(declaredFields(FinanceBusinessOrderDO.class).containsAll(requiredFields));
        assertTrue(Stream.concat(declaredFields(FinanceBusinessOrderRespVO.class).stream(),
                        declaredFields(FinanceBusinessOrderDO.class).stream())
                .noneMatch(genericFields::contains));
    }

    @Test
    void pageRequestShouldFilterBySheetAndGeneratedFields() {
        assertEquals(Set.of("orderNo", "entityCompanyDeptId", "contractProcessId", "productName", "payerName",
                        "importerId", "importDate", "orderDate", "onlyOpenable"),
                declaredFields(FinanceBusinessOrderPageReqVO.class));
    }

    @Test
    void getBusinessOrderShouldNotResolveMissingLegacyImporter() {
        FinanceBusinessOrderService service = mock(FinanceBusinessOrderService.class);
        AdminUserApi adminUserApi = mock(AdminUserApi.class);
        FinanceBusinessOrderController controller = controller(service, adminUserApi);
        when(service.getBusinessOrder(1L)).thenReturn(FinanceBusinessOrderDO.builder().id(1L).build());

        FinanceBusinessOrderRespVO response = controller.getBusinessOrder(1L).getData();

        assertNull(response.getImporterId());
        assertNull(response.getImporterName());
        verifyNoInteractions(adminUserApi);
    }

    @Test
    void getBusinessOrderPageShouldNotResolveMissingLegacyImporter() {
        FinanceBusinessOrderService service = mock(FinanceBusinessOrderService.class);
        AdminUserApi adminUserApi = mock(AdminUserApi.class);
        FinanceBusinessOrderController controller = controller(service, adminUserApi);
        FinanceBusinessOrderPageReqVO request = new FinanceBusinessOrderPageReqVO();
        when(service.getBusinessOrderPage(request)).thenReturn(
                new PageResult<>(List.of(FinanceBusinessOrderDO.builder().id(1L).build()), 1L));

        PageResult<FinanceBusinessOrderRespVO> response = controller.getBusinessOrderPage(request).getData();

        assertNull(response.getList().get(0).getImporterId());
        assertNull(response.getList().get(0).getImporterName());
        verifyNoInteractions(adminUserApi);
    }

    @Test
    void responseVOShouldExposeRemainingBalanceField() {
        assertTrue(declaredFields(FinanceBusinessOrderRespVO.class).contains("remainingBalance"));
        assertTrue(declaredFields(FinanceBusinessOrderRespVO.class).contains("invoiceOpenableAmount"));
        assertTrue(declaredFields(FinanceBusinessOrderRespVO.class).contains("invoicedOccupiedAmount"));
    }

    @Test
    void getBusinessOrderShouldComputeRemainingBalance() {
        FinanceBusinessOrderService service = mock(FinanceBusinessOrderService.class);
        AdminUserApi adminUserApi = mock(AdminUserApi.class);
        FinanceBusinessOrderController controller = controller(service, adminUserApi);
        when(service.getBusinessOrder(1L)).thenReturn(FinanceBusinessOrderDO.builder()
                .id(1L).settlementAmount(new BigDecimal("1000.00"))
                .confirmedClaimedAmount(new BigDecimal("300.00"))
                .invoicedOccupiedAmount(new BigDecimal("200.00")).build());

        FinanceBusinessOrderRespVO response = controller.getBusinessOrder(1L).getData();

        assertEquals(new BigDecimal("700.00"), response.getRemainingBalance());
        assertEquals(new BigDecimal("800.00"), response.getInvoiceOpenableAmount());
    }

    @Test
    void getBusinessOrderPageShouldComputeRemainingBalance() {
        FinanceBusinessOrderService service = mock(FinanceBusinessOrderService.class);
        AdminUserApi adminUserApi = mock(AdminUserApi.class);
        FinanceBusinessOrderController controller = controller(service, adminUserApi);
        FinanceBusinessOrderPageReqVO request = new FinanceBusinessOrderPageReqVO();
        when(service.getBusinessOrderPage(request)).thenReturn(
                new PageResult<>(List.of(FinanceBusinessOrderDO.builder()
                        .id(1L).settlementAmount(new BigDecimal("1000.00"))
                        .confirmedClaimedAmount(new BigDecimal("300.00"))
                        .invoicedOccupiedAmount(new BigDecimal("200.00")).build()), 1L));

        PageResult<FinanceBusinessOrderRespVO> response = controller.getBusinessOrderPage(request).getData();

        assertEquals(new BigDecimal("700.00"), response.getList().get(0).getRemainingBalance());
        assertEquals(new BigDecimal("800.00"), response.getList().get(0).getInvoiceOpenableAmount());
    }

    private static FinanceBusinessOrderController controller(FinanceBusinessOrderService service,
                                                               AdminUserApi adminUserApi) {
        FinanceBusinessOrderController controller = new FinanceBusinessOrderController();
        ReflectionTestUtils.setField(controller, "businessOrderService", service);
        ReflectionTestUtils.setField(controller, "adminUserApi", adminUserApi);
        return controller;
    }

    private static Set<String> declaredFields(Class<?> type) {
        return Stream.of(type.getDeclaredFields()).map(field -> field.getName()).collect(Collectors.toSet());
    }

}
