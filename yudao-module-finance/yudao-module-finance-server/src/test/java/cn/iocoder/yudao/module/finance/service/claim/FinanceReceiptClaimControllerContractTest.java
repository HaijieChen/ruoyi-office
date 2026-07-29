package cn.iocoder.yudao.module.finance.service.claim;

import cn.iocoder.yudao.module.finance.controller.admin.claim.FinanceReceiptClaimController;
import cn.iocoder.yudao.module.finance.controller.admin.claim.vo.FinanceReceiptClaimRevokeAuditRespVO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class FinanceReceiptClaimControllerContractTest {

    @Test
    void revokeAuditListEndpointShouldRequireReviewPermission() throws NoSuchMethodException {
        Method method = FinanceReceiptClaimController.class.getDeclaredMethod(
                "getRevokeAuditList", Long.class);

        GetMapping getMapping = method.getAnnotation(GetMapping.class);
        assertNotNull(getMapping);
        assertArrayEquals(new String[]{"/revoke-audit-list"}, getMapping.value());
        assertEquals("@ss.hasPermission('finance:receipt-claim:review')",
                method.getAnnotation(PreAuthorize.class).value());

        Parameter[] parameters = method.getParameters();
        RequestParam claimIdParam = parameters[0].getAnnotation(RequestParam.class);
        assertEquals("claimId", claimIdParam.value());
        assertTrue(claimIdParam.required());
    }

    @Test
    void revokeAuditRespVOShouldExposePersistedAndDisplayFields() {
        assertEquals(List.of(
                        "id", "claimId", "reviewerId", "reviewerName",
                        "operatorId", "operatorName", "revokeTime", "revokeReason",
                        "reason", "createTime", "action"),
                Stream.of(FinanceReceiptClaimRevokeAuditRespVO.class.getDeclaredFields())
                        .map(java.lang.reflect.Field::getName).toList());
    }

    @Test
    void revokeAuditRespVOShouldHaveCorrectFieldTypes() throws NoSuchFieldException {
        assertEquals(Long.class, FinanceReceiptClaimRevokeAuditRespVO.class.getDeclaredField("id").getType());
        assertEquals(Long.class, FinanceReceiptClaimRevokeAuditRespVO.class.getDeclaredField("claimId").getType());
        assertEquals(Long.class, FinanceReceiptClaimRevokeAuditRespVO.class.getDeclaredField("reviewerId").getType());
        assertEquals(String.class, FinanceReceiptClaimRevokeAuditRespVO.class.getDeclaredField("reviewerName").getType());
        assertEquals(Long.class, FinanceReceiptClaimRevokeAuditRespVO.class.getDeclaredField("operatorId").getType());
        assertEquals(String.class, FinanceReceiptClaimRevokeAuditRespVO.class.getDeclaredField("operatorName").getType());
        assertEquals(LocalDateTime.class, FinanceReceiptClaimRevokeAuditRespVO.class.getDeclaredField("revokeTime").getType());
        assertEquals(String.class, FinanceReceiptClaimRevokeAuditRespVO.class.getDeclaredField("revokeReason").getType());
        assertEquals(String.class, FinanceReceiptClaimRevokeAuditRespVO.class.getDeclaredField("reason").getType());
        assertEquals(LocalDateTime.class, FinanceReceiptClaimRevokeAuditRespVO.class.getDeclaredField("createTime").getType());
        assertEquals(String.class, FinanceReceiptClaimRevokeAuditRespVO.class.getDeclaredField("action").getType());
    }

}
