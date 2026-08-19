package cn.iocoder.yudao.module.finance.service.allocation;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.finance.controller.admin.allocation.vo.FinanceDeptCostAllocationImportExcelVO;
import cn.iocoder.yudao.module.finance.controller.admin.allocation.vo.FinanceDeptCostAllocationImportRespVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.allocation.FinanceDeptCostAllocationDO;
import cn.iocoder.yudao.module.finance.dal.mysql.allocation.FinanceDeptCostAllocationMapper;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.DEPT_ALLOCATION_IMPORT_INVALID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FinanceDeptCostAllocationImportTest {

    private FinanceDeptCostAllocationMapper mapper;
    private DeptApi deptApi;
    private FinanceDeptCostAllocationServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(FinanceDeptCostAllocationMapper.class);
        deptApi = mock(DeptApi.class);
        service = new FinanceDeptCostAllocationServiceImpl(mapper, deptApi);
        AtomicLong ids = new AtomicLong(1);
        doAnswer(inv -> {
            FinanceDeptCostAllocationDO row = inv.getArgument(0);
            row.setId(ids.getAndIncrement());
            return 1;
        }).when(mapper).insert(any(FinanceDeptCostAllocationDO.class));
        when(deptApi.getSimpleDeptList()).thenReturn(CommonResult.success(List.of(
                dept(11L, "研发"),
                dept(22L, "市场")
        )));
    }

    @Test
    void importTwoDeptsShouldInsertWithImporterAudit() {
        FinanceDeptCostAllocationImportRespVO resp = service.importAllocationList(
                List.of(row("2026-08", "研发", null, "10.50", "a"),
                        row("2026-08", null, "22", "20", "b")),
                "薪资", 9L);

        assertEquals(2, resp.getCreatedCount());
        verify(mapper).deleteByPeriodAndSourceType("2026-08", "薪资");
        ArgumentCaptor<FinanceDeptCostAllocationDO> captor =
                ArgumentCaptor.forClass(FinanceDeptCostAllocationDO.class);
        verify(mapper, times(2)).insert(captor.capture());
        List<FinanceDeptCostAllocationDO> inserted = captor.getAllValues();
        assertEquals(11L, inserted.get(0).getDeptId());
        assertEquals("研发", inserted.get(0).getDeptName());
        assertEquals(0, new BigDecimal("10.50").compareTo(inserted.get(0).getAmount()));
        assertEquals(22L, inserted.get(1).getDeptId());
        assertEquals("市场", inserted.get(1).getDeptName());
        inserted.forEach(row -> {
            assertEquals("2026-08", row.getPeriod());
            assertEquals("薪资", row.getSourceType());
            assertEquals(9L, row.getImporterId());
            assertNotNull(row.getImportTime());
        });
    }

    @Test
    void reimportSamePeriodAndSourceTypeReplacesAfterFullValidation() {
        service.importAllocationList(
                List.of(row("2026-08", "研发", null, "10", null),
                        row("2026-08", "市场", null, "20", null)),
                "薪资", 8L);

        FinanceDeptCostAllocationImportRespVO resp = service.importAllocationList(
                List.of(row("2026-08", "研发", null, "99", "覆盖")),
                "薪资", 9L);

        assertEquals(1, resp.getCreatedCount());
        verify(mapper, times(2)).deleteByPeriodAndSourceType("2026-08", "薪资");
        ArgumentCaptor<FinanceDeptCostAllocationDO> captor =
                ArgumentCaptor.forClass(FinanceDeptCostAllocationDO.class);
        verify(mapper, times(3)).insert(captor.capture());
        FinanceDeptCostAllocationDO last = captor.getAllValues().get(2);
        assertEquals(0, new BigDecimal("99").compareTo(last.getAmount()));
        assertEquals("覆盖", last.getRemark());
        assertEquals(9L, last.getImporterId());
        assertNotNull(last.getImportTime());
    }

    @Test
    void unknownDeptRejectsWholeFileAndLeavesExistingRows() {
        service.importAllocationList(
                List.of(row("2026-08", "研发", null, "10", null),
                        row("2026-08", "市场", null, "20", null)),
                "薪资", 8L);

        ServiceException ex = assertThrows(ServiceException.class, () ->
                service.importAllocationList(
                        List.of(row("2026-08", "研发", null, "1", null),
                                row("2026-08", "幽灵部", null, "2", null)),
                        "薪资", 9L));
        assertEquals(DEPT_ALLOCATION_IMPORT_INVALID.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("幽灵部"));

        verify(mapper, times(1)).deleteByPeriodAndSourceType("2026-08", "薪资");
        verify(mapper, times(2)).insert(any(FinanceDeptCostAllocationDO.class));
    }

    private static FinanceDeptCostAllocationImportExcelVO row(
            String period, String deptName, String deptId, String amount, String remark) {
        return FinanceDeptCostAllocationImportExcelVO.builder()
                .period(period)
                .deptName(deptName)
                .deptId(deptId)
                .amount(amount)
                .remark(remark)
                .build();
    }

    private static DeptRespDTO dept(Long id, String name) {
        DeptRespDTO dto = new DeptRespDTO();
        dto.setId(id);
        dto.setName(name);
        return dto;
    }
}
