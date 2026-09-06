package cn.iocoder.yudao.module.bpm.service.oa;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OaOvertimeCalendarNoticeLocatorTest {

    @Test
    void extractsAbsoluteAndRelativeNoticeLinks() {
        String html = """
                <html>国务院
                <a href="/zhengce/zhengceku/202511/content_7047091.htm">国务院办公厅关于2026年部分节假日安排的通知</a>
                </html>
                """;
        List<String> urls = OaOvertimeCalendarNoticeLocator.noticeUrls(
                2026, html, "https://www.gov.cn/zhengce/zhengceku/");
        assertEquals(List.of("https://www.gov.cn/zhengce/zhengceku/202511/content_7047091.htm"), urls);
        assertTrue(OaOvertimeCalendarNoticeLocator.noticeYears(html).contains(2026));
        assertTrue(OaOvertimeCalendarNoticeLocator.looksLikePolicyListing(html + "政策文件库"));
    }
}
