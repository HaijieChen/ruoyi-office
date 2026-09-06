package cn.iocoder.yudao.module.bpm.service.oa;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从国务院政策文件库页面发现「YYYY年部分节假日安排」正文链接。
 */
public final class OaOvertimeCalendarNoticeLocator {

    public static final List<String> LISTING_URLS = List.of(
            "https://www.gov.cn/zhengce/zhengcewenjianku/",
            "https://www.gov.cn/zhengce/zhengceku/",
            "https://www.gov.cn/zhengce/zuixin/"
    );

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
