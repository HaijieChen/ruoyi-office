package cn.iocoder.yudao.module.bpm.service.oa;

import cn.iocoder.yudao.module.bpm.dal.dataobject.oa.BpmOAOvertimeCalendarVersionDO;
import cn.iocoder.yudao.module.bpm.dal.mysql.oa.BpmOAOvertimeCalendarVersionMapper;
import cn.iocoder.yudao.module.bpm.dal.mysql.oa.BpmOAOvertimeMapper;
import cn.iocoder.yudao.module.system.api.notify.NotifyMessageSendApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OaOvertimeCalendarVersionServiceTest {

    private BpmOAOvertimeCalendarVersionMapper versionMapper;
    private BpmOAOvertimeMapper overtimeMapper;
    private NotifyMessageSendApi notifyApi;
    private OaOvertimeCalendarVersionService service;
    private final List<BpmOAOvertimeCalendarVersionDO> store = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong();

    @BeforeEach
    void setUp() {
        versionMapper = mock(BpmOAOvertimeCalendarVersionMapper.class);
        overtimeMapper = mock(BpmOAOvertimeMapper.class);
        notifyApi = mock(NotifyMessageSendApi.class);
        service = new OaOvertimeCalendarVersionService();
        ReflectionTestUtils.setField(service, "versionMapper", versionMapper);
        ReflectionTestUtils.setField(service, "notifyMessageSendApi", notifyApi);
        service.setClock(Clock.fixed(Instant.parse("2026-10-05T01:00:00Z"), ZoneOffset.UTC));
        when(versionMapper.insert(any(BpmOAOvertimeCalendarVersionDO.class))).thenAnswer(inv -> {
            BpmOAOvertimeCalendarVersionDO row = inv.getArgument(0);
            row.setId(ids.incrementAndGet());
            store.add(row);
            return 1;
        });
        when(versionMapper.selectByYearAndHash(any(Integer.class), any())).thenAnswer(inv ->
                store.stream()
                        .filter(r -> r.getCalendarYear().equals(inv.getArgument(0))
                                && r.getContentHash().equals(inv.getArgument(1)))
                        .findFirst().orElse(null));
        when(versionMapper.selectActiveByYear(any(Integer.class))).thenAnswer(inv ->
                store.stream()
                        .filter(r -> r.getCalendarYear().equals(inv.getArgument(0))
                                && BpmOAOvertimeCalendarVersionDO.ACTIVE.equals(r.getStatus()))
                        .findFirst().orElse(null));
        when(versionMapper.selectById(any())).thenAnswer(inv ->
                store.stream().filter(r -> r.getId().equals(inv.getArgument(0))).findFirst().orElse(null));
        org.mockito.Mockito.doAnswer(inv ->
                store.stream().filter(r -> BpmOAOvertimeCalendarVersionDO.ACTIVE.equals(r.getStatus())).toList())
                .when(versionMapper)
                .selectList(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.toolkit.support.SFunction<BpmOAOvertimeCalendarVersionDO, ?>>any(),
                        org.mockito.ArgumentMatchers.any());
        when(versionMapper.updateById(any(BpmOAOvertimeCalendarVersionDO.class))).thenReturn(1);
    }

    @AfterEach
    void resetOverlay() {
        OaOvertimeCalendar.replaceActiveYears(java.util.Map.of());
    }

    @Test
    void fetch_sameHash_doesNotInsertAgain() {
        service.setHttpGet(url -> OaOvertimeCalendarNoticeParserTest.NOTICE_2026);
        String first = service.fetchYear(2026);
        assertTrue(first.startsWith("pending:"));
        String second = service.fetchYear(2026);
        assertEquals("same:1", second);
        assertEquals(1, store.size());
    }

    @Test
    void fetch_failure_keepsActiveAndRecordsFailed() {
        OaOvertimeCalendar.YearData seed = OaOvertimeCalendar.yearData(2026);
        service.importSeedActive(seed, seed.sourceUrl);
        assertEquals(BpmOAOvertimeCalendarVersionDO.ACTIVE, store.get(0).getStatus());
        service.setHttpGet(url -> {
            throw new java.io.IOException("timeout");
        });
        String result = service.fetchYear(2026);
        assertEquals("failed-kept-active", result);
        assertEquals(BpmOAOvertimeCalendarVersionDO.ACTIVE, store.get(0).getStatus());
        assertTrue(store.stream().anyMatch(r -> BpmOAOvertimeCalendarVersionDO.FAILED.equals(r.getStatus())));
    }

    @Test
    void enable_doesNotRewriteHistoricalOvertimeRows() {
        service.setHttpGet(url -> OaOvertimeCalendarNoticeParserTest.NOTICE_2026);
        service.fetchYear(2026);
        service.verifyAndEnable(1L, 9L, true);
        verify(overtimeMapper, never()).updateById(any(cn.iocoder.yudao.module.bpm.dal.dataobject.oa.BpmOAOvertimeDO.class));
        verify(overtimeMapper, never()).insert(any(cn.iocoder.yudao.module.bpm.dal.dataobject.oa.BpmOAOvertimeDO.class));
        assertEquals(BpmOAOvertimeCalendarVersionDO.ACTIVE, store.get(0).getStatus());
        assertEquals(OaOvertimeCalendar.DayKind.LEGAL_HOLIDAY,
                OaOvertimeCalendar.kind(LocalDate.of(2026, 5, 1)));
        assertNotEquals(OaOvertimeCalendar.DayKind.LEGAL_HOLIDAY,
                OaOvertimeCalendar.kind(LocalDate.of(2026, 5, 3)));
    }

    @Test
    void fetchDueYears_inOctober_includesNextYear() {
        service.setHttpGet(url -> "<html>no 2027 notice</html>");
        String result = service.fetchDueYears();
        assertTrue(result.contains("2026="));
        assertTrue(result.contains("2027="));
    }
}
