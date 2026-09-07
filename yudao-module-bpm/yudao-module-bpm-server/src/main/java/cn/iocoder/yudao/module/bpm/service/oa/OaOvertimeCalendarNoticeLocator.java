package cn.iocoder.yudao.module.bpm.service.oa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从国务院政策文件库页面 / 官方搜索发现「YYYY年部分节假日安排」正文链接。
 */
public final class OaOvertimeCalendarNoticeLocator {

    public static final List<String> LISTING_URLS = List.of(
            "https://www.gov.cn/zhengce/zhengcewenjianku/",
            "https://www.gov.cn/zhengce/zhengceku/",
            "https://www.gov.cn/zhengce/zuixin/"
    );

    public static final String SEARCH_DATA_URL = "https://sousuo.www.gov.cn/search-gov/data";
    public static final String SEARCH_REFERER = "https://sousuo.www.gov.cn/zcwjk/policyDocumentLibrary";
    public static final String COVERAGE_QUERY = "节假日安排";

    private static final ObjectMapper JSON = new ObjectMapper();

    static final Pattern TITLE_YEAR = Pattern.compile("(\\d{4})年部分节假日安排");
    private static final Pattern HREF_THEN_TITLE = Pattern.compile(
            "href\\s*=\\s*[\"']([^\"']+)[\"'][^>]{0,200}>([^<]{0,120}?(\\d{4})年部分节假日安排)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE_THEN_HREF = Pattern.compile(
            "(\\d{4})年部分节假日安排.{0,160}?href\\s*=\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private OaOvertimeCalendarNoticeLocator() {
    }

    public static List<String> noticeUrls(int year, String listingHtml, String listingUrl) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        if (listingHtml == null || listingHtml.isBlank()) {
            return List.of();
        }
        Matcher hrefTitle = HREF_THEN_TITLE.matcher(listingHtml);
        while (hrefTitle.find()) {
            if (Integer.parseInt(hrefTitle.group(3)) == year) {
                addIfAllowed(urls, resolve(hrefTitle.group(1), listingUrl));
            }
        }
        Matcher titleHref = TITLE_THEN_HREF.matcher(listingHtml);
        while (titleHref.find()) {
            if (Integer.parseInt(titleHref.group(1)) == year) {
                addIfAllowed(urls, resolve(titleHref.group(2), listingUrl));
            }
        }
        return new ArrayList<>(urls);
    }

    public static Set<Integer> noticeYears(String html) {
        LinkedHashSet<Integer> years = new LinkedHashSet<>();
        if (html == null) {
            return years;
        }
        Matcher matcher = TITLE_YEAR.matcher(html);
        while (matcher.find()) {
            years.add(Integer.parseInt(matcher.group(1)));
        }
        return years;
    }

    public static boolean looksLikePolicyListing(String html) {
        if (html == null || html.isBlank()) {
            return false;
        }
        return html.contains("国务院") && (html.contains("节假日安排") || html.contains("政策文件库")
                || html.contains("zhengcewenjianku") || html.contains("政府信息公开"));
    }

    public static String yearSearchQuery(int year) {
        return year + "年部分节假日安排";
    }

    public static String officialSearchUrl(String query) {
        String q = URLEncoder.encode(query == null ? "" : query, StandardCharsets.UTF_8);
        return SEARCH_DATA_URL
                + "?t=zhengcelibrary&q=" + q
                + "&timetype=&mintime=&maxtime=&sort=score&sortType=1&searchfield=title"
                + "&pcodeJiguan=&childtype=&subchildtype=&tsbq=&pubtimeyear=&puborg="
                + "&pcodeYear=&pcodeNum=&filetype=&p=1&n=20&inpro=&bmfl=&dup=&orpro="
                + "&type=gwyzcwjk";
    }

    public static boolean looksLikeOfficialSearch(String json) {
        JsonNode root = readJson(json);
        if (root == null) {
            return false;
        }
        int code = root.path("code").asInt(0);
        if (code == 1001) {
            return true;
        }
        if (code != 200) {
            return false;
        }
        return root.path("searchVO").path("catMap").path("gongwen").path("listVO").isArray();
    }

    public static boolean isExplicitEmptySearch(String json) {
        JsonNode root = readJson(json);
        if (root == null) {
            return false;
        }
        int code = root.path("code").asInt(0);
        if (code == 1001) {
            return true;
        }
        if (code != 200) {
            return false;
        }
        JsonNode gongwen = root.path("searchVO").path("catMap").path("gongwen");
        JsonNode listVO = gongwen.path("listVO");
        return listVO.isArray() && listVO.size() == 0 && gongwen.path("totalCount").asInt(-1) == 0;
    }

    public static Set<Integer> searchYears(String json) {
        LinkedHashSet<Integer> years = new LinkedHashSet<>();
        for (JsonNode item : searchItems(readJson(json))) {
            years.addAll(noticeYears(plainTitle(item)));
        }
        return years;
    }

    public static List<String> searchNoticeUrls(int year, String json) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        for (JsonNode item : searchItems(readJson(json))) {
            if (!noticeYears(plainTitle(item)).contains(year)) {
                continue;
            }
            addIfAllowed(urls, resolve(item.path("url").asText(null), "https://www.gov.cn/"));
        }
        return new ArrayList<>(urls);
    }

    public enum YearSearchKind {
        HIT, EMPTY, UNUSABLE, INVALID
    }

    public static final class YearSearchResult {
        public final YearSearchKind kind;
        public final List<String> urls;

        public YearSearchResult(YearSearchKind kind, List<String> urls) {
            this.kind = kind;
            this.urls = urls;
        }
    }

    public static YearSearchResult classifyYearSearch(int year, String json) {
        if (!looksLikeOfficialSearch(json)) {
            return new YearSearchResult(YearSearchKind.INVALID, List.of());
        }
        if (isExplicitEmptySearch(json)) {
            return new YearSearchResult(YearSearchKind.EMPTY, List.of());
        }
        List<String> urls = searchNoticeUrls(year, json);
        if (!urls.isEmpty()) {
            return new YearSearchResult(YearSearchKind.HIT, urls);
        }
        return new YearSearchResult(YearSearchKind.UNUSABLE, List.of());
    }

    private static JsonNode readJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSON.readTree(json);
        } catch (Exception ex) {
            return null;
        }
    }

    private static Iterable<JsonNode> searchItems(JsonNode root) {
        ArrayList<JsonNode> items = new ArrayList<>();
        if (root == null) {
            return items;
        }
        JsonNode gongwen = root.path("searchVO").path("catMap").path("gongwen").path("listVO");
        if (gongwen.isArray()) {
            gongwen.forEach(items::add);
        }
        JsonNode top = root.path("searchVO").path("listVO");
        if (top.isArray()) {
            top.forEach(items::add);
        }
        return items;
    }

    private static String plainTitle(JsonNode item) {
        if (item == null) {
            return "";
        }
        return item.path("title").asText("").replaceAll("<[^>]+>", "");
    }

    public static String resolve(String href, String pageUrl) {
        if (href == null || href.isBlank() || href.startsWith("javascript:")) {
            return null;
        }
        try {
            URI base = URI.create(pageUrl);
            URI resolved = base.resolve(href.trim());
            if (!"www.gov.cn".equals(resolved.getHost())) {
                return null;
            }
            String scheme = resolved.getScheme();
            if (!"https".equals(scheme) && !"http".equals(scheme)) {
                return null;
            }
            return resolved.toString();
        } catch (Exception ex) {
            return null;
        }
    }

    private static void addIfAllowed(Set<String> urls, String url) {
        if (url != null) {
            urls.add(url);
        }
    }
}
