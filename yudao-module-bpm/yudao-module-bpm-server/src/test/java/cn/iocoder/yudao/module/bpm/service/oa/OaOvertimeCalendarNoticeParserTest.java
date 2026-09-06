package cn.iocoder.yudao.module.bpm.service.oa;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OaOvertimeCalendarNoticeParserTest {

    static final String NOTICE_2026 = """
            国务院办公厅关于2026年部分节假日安排的通知
            一、元旦：1月1日（周四）至3日（周六）放假调休，共3天。1月4日（周日）上班。
            二、春节：2月15日（农历腊月二十八、周日）至23日（农历正月初七、周一）放假调休，共9天。2月14日（周六）、2月28日（周六）上班。
            三、清明节：4月4日（周六）至6日（周一）放假，共3天。
            四、劳动节：5月1日（周五）至5日（周二）放假调休，共5天。5月9日（周六）上班。
            五、端午节：6月19日（周五）至21日（周日）放假，共3天。
            六、中秋节：9月25日（周五）至27日（周日）放假，共3天。
            七、国庆节：10月1日（周四）至7日（周三）放假调休，共7天。9月20日（周日）、10月10日（周六）上班。
            """;

    @Test
    void parse2026_legalThirteen_laborDayTwoDays() {
        Optional<OaOvertimeCalendar.YearData> parsed = OaOvertimeCalendarNoticeParser.parse(2026, NOTICE_2026);
        assertTrue(parsed.isPresent());
        OaOvertimeCalendar.YearData data = parsed.get();
        assertEquals(13, data.legalHolidays.size());
        assertTrue(data.legalHolidays.containsAll(List.of(
                "2026-01-01", "2026-02-16", "2026-02-17", "2026-02-18", "2026-02-19",
                "2026-04-04", "2026-05-01", "2026-05-02", "2026-06-19", "2026-09-25",
                "2026-10-01", "2026-10-02", "2026-10-03")));
        assertFalse(data.legalHolidays.contains("2026-05-03"));
        assertTrue(data.makeupWorkdays.containsAll(List.of(
                "2026-01-04", "2026-02-14", "2026-02-28", "2026-05-09", "2026-09-20", "2026-10-10")));
        assertTrue(data.makeupRestDays.contains("2026-02-20"));
        assertTrue(data.makeupRestDays.contains("2026-05-04"));
        assertFalse(data.makeupRestDays.contains("2026-05-03"));
        assertEquals(OaOvertimeCalendar.DayKind.WEEKEND, OaOvertimeCalendar.classify(data, java.time.LocalDate.of(2026, 5, 3)));
    }

    @Test
    void parse_emptyOrWrongYear_isEmpty() {
        assertTrue(OaOvertimeCalendarNoticeParser.parse(2026, "").isEmpty());
        assertTrue(OaOvertimeCalendarNoticeParser.parse(2027, NOTICE_2026).isEmpty());
        assertTrue(OaOvertimeCalendarNoticeParser.parse(2026, "<html>no holiday notice</html>").isEmpty());
    }

    @Test
    void parse2026_officialHtmlStrongTags_legalThirteen() {
        String html = """
                <html><body>
                国务院办公厅关于2026年部分节假日安排的通知
                <p><strong>一、元旦：</strong>1月1日（周四）至3日（周六）放假调休，共3天。1月4日（周日）上班。</p>
                <p><strong>二、春节：</strong>2月15日（农历腊月二十八、周日）至23日（农历正月初七、周一）放假调休，共9天。2月14日（周六）、2月28日（周六）上班。</p>
                <p><strong>三、清明节：</strong>4月4日（周六）至6日（周一）放假，共3天。</p>
                <p><strong>四、劳动节：</strong>5月1日（周五）至5日（周二）放假调休，共5天。5月9日（周六）上班。</p>
                <p><strong>五、端午节：</strong>6月19日（周五）至21日（周日）放假，共3天。</p>
                <p><strong>六、中秋节：</strong>9月25日（周五）至27日（周日）放假，共3天。</p>
                <p><strong>七、国庆节：</strong>10月1日（周四）至7日（周三）放假调休，共7天。9月20日（周日）、10月10日（周六）上班。</p>
                </body></html>
                """;
        Optional<OaOvertimeCalendar.YearData> parsed = OaOvertimeCalendarNoticeParser.parse(2026, html);
        assertTrue(parsed.isPresent());
        assertEquals(13, parsed.get().legalHolidays.size());
        assertTrue(parsed.get().legalHolidays.containsAll(List.of("2026-05-01", "2026-05-02")));
        assertFalse(parsed.get().legalHolidays.contains("2026-05-03"));
    }

    @Test
    void parse2026_officialGovHtmlFixture_legalThirteen() throws Exception {
        String html = new String(Objects.requireNonNull(
                getClass().getResourceAsStream("/oa/overtime-notice-2026-gov.html")).readAllBytes(),
                StandardCharsets.UTF_8);
        Optional<OaOvertimeCalendar.YearData> parsed = OaOvertimeCalendarNoticeParser.parse(2026, html);
        assertTrue(parsed.isPresent());
        assertEquals(13, parsed.get().legalHolidays.size());
        assertTrue(parsed.get().legalHolidays.contains("2026-05-01"));
        assertFalse(parsed.get().legalHolidays.contains("2026-05-03"));
    }
}
