package cn.iocoder.yudao.module.bpm.service.oa;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OaOvertimeCalendarNoticeLocatorTest {

    static final String SEARCH_2026 = """
            {"code":200,"msg":"操作成功","searchVO":{"catMap":{"gongwen":{"totalCount":1,"listVO":[
            {"title":"国务院办公厅关于<em>2026</em>年部分节假日安排的通知",
             "url":"https://www.gov.cn/zhengce/zhengceku/202511/content_7047091.htm","pcode":"国办发明电〔2025〕7号"}
            ]}}}}
            """;
    static final String SEARCH_EMPTY = """
            {"code":1001,"msg":"抱歉，没有找到相关结果","data":[],"searchVO":null}
            """;
    static final String SEARCH_INCOMPLETE = """
            {"code":200,"msg":"操作成功","searchVO":{"q":null,"totalCount":0,"listVO":null,"catMap":{}}}
            """;

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

    @Test
    void officialSearchUrl_usesDocumentLibraryParams() {
        String url = OaOvertimeCalendarNoticeLocator.officialSearchUrl("2026年部分节假日安排");
        assertTrue(url.startsWith("https://sousuo.www.gov.cn/search-gov/data?"));
        assertTrue(url.contains("t=zhengcelibrary"));
        assertTrue(url.contains("type=gwyzcwjk"));
        assertTrue(url.contains("2026"));
        assertTrue(url.contains("n=20"));
    }

    @Test
    void parseSearchJson_extractsYearAndNoticeUrl() {
        assertTrue(OaOvertimeCalendarNoticeLocator.looksLikeOfficialSearch(SEARCH_2026));
        assertTrue(OaOvertimeCalendarNoticeLocator.searchYears(SEARCH_2026).contains(2026));
        assertEquals(List.of("https://www.gov.cn/zhengce/zhengceku/202511/content_7047091.htm"),
                OaOvertimeCalendarNoticeLocator.searchNoticeUrls(2026, SEARCH_2026));
        assertTrue(OaOvertimeCalendarNoticeLocator.searchNoticeUrls(2027, SEARCH_2026).isEmpty());
    }

    @Test
    void parseSearchJson_code1001_isOfficialEmpty() {
        assertTrue(OaOvertimeCalendarNoticeLocator.looksLikeOfficialSearch(SEARCH_EMPTY));
        assertTrue(OaOvertimeCalendarNoticeLocator.searchYears(SEARCH_EMPTY).isEmpty());
        assertTrue(OaOvertimeCalendarNoticeLocator.searchNoticeUrls(2027, SEARCH_EMPTY).isEmpty());
    }

    @Test
    void parseSearchJson_incompleteParams_isNotOfficialCoverage() {
        assertFalse(OaOvertimeCalendarNoticeLocator.looksLikeOfficialSearch(SEARCH_INCOMPLETE));
    }

    @Test
    void classifyYearSearch_emptyHitUnusable() {
        assertEquals(OaOvertimeCalendarNoticeLocator.YearSearchKind.EMPTY,
                OaOvertimeCalendarNoticeLocator.classifyYearSearch(2027, SEARCH_EMPTY).kind);
        assertEquals(OaOvertimeCalendarNoticeLocator.YearSearchKind.HIT,
                OaOvertimeCalendarNoticeLocator.classifyYearSearch(2026, SEARCH_2026).kind);
        assertEquals(OaOvertimeCalendarNoticeLocator.YearSearchKind.INVALID,
                OaOvertimeCalendarNoticeLocator.classifyYearSearch(2026, SEARCH_INCOMPLETE).kind);
        String unusable = """
                {"code":200,"msg":"操作成功","searchVO":{"catMap":{"gongwen":{"totalCount":1,"listVO":[
                {"title":"国务院办公厅关于2027年部分节假日安排的通知","url":"https://example.com/x.htm"}]}}}}
                """;
        assertEquals(OaOvertimeCalendarNoticeLocator.YearSearchKind.UNUSABLE,
                OaOvertimeCalendarNoticeLocator.classifyYearSearch(2027, unusable).kind);
    }

    @Test
    void classifyYearSearch_code200EmptyList_isEmpty() {
        String empty200 = """
                {"code":200,"msg":"操作成功","searchVO":{"catMap":{"gongwen":{"totalCount":0,"listVO":[]}}}}
                """;
        assertTrue(OaOvertimeCalendarNoticeLocator.looksLikeOfficialSearch(empty200));
        assertEquals(OaOvertimeCalendarNoticeLocator.YearSearchKind.EMPTY,
                OaOvertimeCalendarNoticeLocator.classifyYearSearch(2027, empty200).kind);
    }

    @Test
    void classifyYearSearch_nonEmptyWithoutTargetTitle_isUnusableNotEmpty() {
        assertEquals(OaOvertimeCalendarNoticeLocator.YearSearchKind.UNUSABLE,
                OaOvertimeCalendarNoticeLocator.classifyYearSearch(2027, SEARCH_2026).kind);
        assertFalse(OaOvertimeCalendarNoticeLocator.YearSearchKind.EMPTY.equals(
                OaOvertimeCalendarNoticeLocator.classifyYearSearch(2027, SEARCH_2026).kind));
    }
}
