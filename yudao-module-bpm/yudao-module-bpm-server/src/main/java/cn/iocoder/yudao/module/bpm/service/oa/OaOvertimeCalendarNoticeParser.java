package cn.iocoder.yudao.module.bpm.service.oa;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 解析国务院「部分节假日安排」公告。法定日按国令第795号映射，不把连休整段标成法定。
 */
public final class OaOvertimeCalendarNoticeParser {

    private static final Pattern YEAR = Pattern.compile("(\\d{4})年部分节假日安排");
    private static final Pattern RANGE = Pattern.compile(
            "(元旦|春节|清明节|劳动节|端午节|中秋节|国庆节)：\\s*(\\d{1,2})月(\\d{1,2})日.{0,80}?至(\\d{1,2})日");
    private static final Pattern SPRING_CHU7 = Pattern.compile(
            "春节：.*?至(\\d{1,2})日（农历正月初([一二三四五六七八九])");

    private OaOvertimeCalendarNoticeParser() {
    }

    public static Optional<OaOvertimeCalendar.YearData> parse(int expectedYear, String html) {
        if (html == null || html.isBlank()) {
            return Optional.empty();
        }
        Matcher yearMatcher = YEAR.matcher(html);
        if (!yearMatcher.find()) {
            return Optional.empty();
        }
        int year = Integer.parseInt(yearMatcher.group(1));
        if (year != expectedYear) {
            return Optional.empty();
        }
        List<String> makeupWork = new ArrayList<>();
        for (String clause : html.split("[。\\n]")) {
            if (!clause.contains("上班")) {
                continue;
            }
            Matcher workMatcher = Pattern.compile("(\\d{1,2})月(\\d{1,2})日").matcher(clause);
            while (workMatcher.find()) {
                makeupWork.add(iso(year, Integer.parseInt(workMatcher.group(1)),
                        Integer.parseInt(workMatcher.group(2))));
            }
        }
        List<String> legal = legalDaysFrom795(year, html);
        if (legal.size() != 13) {
            return Optional.empty();
        }
        List<String> makeupRest = makeupRestDays(year, html, legal, makeupWork);
        OaOvertimeCalendar.YearData data = new OaOvertimeCalendar.YearData();
        data.year = year;
        data.source = "国务院部分节假日安排公告";
        data.legalHolidays = List.copyOf(legal);
        data.makeupWorkdays = List.copyOf(makeupWork);
        data.makeupRestDays = List.copyOf(makeupRest);
        data.weekends = OaOvertimeCalendar.weekendsOf(data);
        return Optional.of(data);
    }

    static List<String> legalDaysFrom795(int year, String html) {
        List<String> legal = new ArrayList<>();
        legal.add(iso(year, 1, 1));
        springLegal(year, html).ifPresent(days -> legal.addAll(days));
        festivalStart(html, "清明").ifPresent(d -> legal.add(d.toString()));
        legal.add(iso(year, 5, 1));
        legal.add(iso(year, 5, 2));
        festivalStart(html, "端午").ifPresent(d -> legal.add(d.toString()));
        festivalStart(html, "中秋").ifPresent(d -> legal.add(d.toString()));
        legal.add(iso(year, 10, 1));
        legal.add(iso(year, 10, 2));
        legal.add(iso(year, 10, 3));
        return legal.stream().distinct().sorted().collect(Collectors.toList());
    }

    private static Optional<List<String>> springLegal(int year, String html) {
        Matcher matcher = SPRING_CHU7.matcher(html);
        if (!matcher.find()) {
            return Optional.empty();
        }
        int endDay = Integer.parseInt(matcher.group(1));
        int chu = "一二三四五六七八九".indexOf(matcher.group(2)) + 1;
        LocalDate rangeStart = festivalStart(html, "春节").orElse(null);
        if (rangeStart == null) {
            return Optional.empty();
        }
        LocalDate end = rangeStart.withDayOfMonth(endDay);
        if (end.isBefore(rangeStart)) {
            end = rangeStart.plusMonths(1).withDayOfMonth(endDay);
        }
        LocalDate chuyi = end.minusDays(chu - 1);
        LocalDate chuxi = chuyi.minusDays(1);
        List<String> days = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            days.add(chuxi.plusDays(i).toString());
        }
        return Optional.of(days);
    }

    private static Optional<LocalDate> festivalStart(String html, String name) {
        Matcher matcher = RANGE.matcher(html);
        while (matcher.find()) {
            if (matcher.group(1).contains(name.replace("节", ""))) {
                return Optional.of(LocalDate.of(htmlYear(html),
                        Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3))));
            }
        }
        return Optional.empty();
    }

    private static int htmlYear(String html) {
        Matcher yearMatcher = YEAR.matcher(html);
        return yearMatcher.find() ? Integer.parseInt(yearMatcher.group(1)) : 0;
    }

    private static List<String> makeupRestDays(int year, String html, List<String> legal, List<String> makeupWork) {
        List<String> rest = new ArrayList<>();
        Matcher matcher = RANGE.matcher(html);
        while (matcher.find()) {
            int startMonth = Integer.parseInt(matcher.group(2));
            int startDay = Integer.parseInt(matcher.group(3));
            int endDay = Integer.parseInt(matcher.group(4));
            LocalDate cursor = LocalDate.of(year, startMonth, startDay);
            LocalDate end = LocalDate.of(year, startMonth, startDay).withDayOfMonth(endDay);
            if (end.isBefore(cursor)) {
                end = LocalDate.of(year, startMonth, endDay).plusMonths(1).withDayOfMonth(endDay);
            }
            // 国庆 10月1日至7日 同月；中秋跨月很少。endDay < startDay 用下月。
            if (endDay < startDay) {
                end = cursor.plusMonths(1).withDayOfMonth(endDay);
            } else {
                end = LocalDate.of(year, startMonth, endDay);
            }
            while (!cursor.isAfter(end)) {
                String iso = cursor.toString();
                DayOfWeek dow = cursor.getDayOfWeek();
                boolean weekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
                if (!legal.contains(iso) && !makeupWork.contains(iso) && !weekend) {
                    rest.add(iso);
                }
                cursor = cursor.plusDays(1);
            }
        }
        return rest.stream().distinct().sorted().collect(Collectors.toList());
    }

    private static String iso(int year, int month, int day) {
        return LocalDate.of(year, month, day).toString();
    }
}
