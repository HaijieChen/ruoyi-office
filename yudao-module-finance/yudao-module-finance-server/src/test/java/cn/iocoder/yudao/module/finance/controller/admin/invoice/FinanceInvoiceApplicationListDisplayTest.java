package cn.iocoder.yudao.module.finance.controller.admin.invoice;

import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationRespVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationLineDO;
import cn.iocoder.yudao.module.finance.dal.mysql.invoice.FinanceInvoiceApplicationLineMapper;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FinanceInvoiceApplicationListDisplayTest {

    @Test
    void fillShouldWriteApplicantNicknameAndLatestIssuedAt() {
        AdminUserApi userApi = mock(AdminUserApi.class);
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(7L);
        user.setNickname("张三");
        when(userApi.getUserMap(anyCollection())).thenReturn(Map.of(7L, user));

        FinanceInvoiceApplicationLineMapper lineMapper = mock(FinanceInvoiceApplicationLineMapper.class);
        LocalDateTime earlier = LocalDateTime.of(2026, 1, 1, 8, 0);
        LocalDateTime later = LocalDateTime.of(2026, 3, 2, 15, 30);
        when(lineMapper.selectListByApplicationIds(anyCollection())).thenReturn(List.of(
                FinanceInvoiceApplicationLineDO.builder().applicationId(1L).issuedAt(earlier).build(),
                FinanceInvoiceApplicationLineDO.builder().applicationId(1L).issuedAt(later).build(),
                FinanceInvoiceApplicationLineDO.builder().applicationId(2L).issuedAt(null).build()));

        FinanceInvoiceApplicationRespVO issued = new FinanceInvoiceApplicationRespVO();
        issued.setId(1L);
        issued.setApplicantUserId(7L);
        issued.setInvoiceCompany("示例主体");
        issued.setRemark("加急");
        FinanceInvoiceApplicationRespVO pending = new FinanceInvoiceApplicationRespVO();
        pending.setId(2L);
        pending.setApplicantUserId(7L);

        FinanceInvoiceApplicationListDisplay.fill(List.of(issued, pending), userApi, lineMapper);

        assertEquals("张三", issued.getApplicantName());
        assertEquals("张三", pending.getApplicantName());
        assertEquals(later, issued.getIssuedAt());
        assertNull(pending.getIssuedAt());
        assertEquals("示例主体", issued.getInvoiceCompany());
        assertEquals("加急", issued.getRemark());
    }

    @Test
    void fillShouldTolerateEmptyPage() {
        FinanceInvoiceApplicationListDisplay.fill(List.of(), mock(AdminUserApi.class),
                mock(FinanceInvoiceApplicationLineMapper.class));
        FinanceInvoiceApplicationListDisplay.fill(null, null, null);
    }
}
