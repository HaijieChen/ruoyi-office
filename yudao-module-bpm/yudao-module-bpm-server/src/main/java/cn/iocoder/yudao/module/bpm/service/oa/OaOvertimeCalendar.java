package cn.iocoder.yudao.module.bpm.service.oa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 加班可申请日期：法定节假日，或普通周末（周六日且非调休上班）。
 * 调休形成的工作日休息不扩大为可申请。数据按年版本化，缺年不编造。
 */
public final class OaOvertimeCalendar {

    public enum DayKind {
        LEGAL_HOLIDAY,
        WEEKEND,
        MAKEUP_WORKDAY,
        MAKEUP_REST,
        WEEKDAY
    }

    public static final class CalendarMissingException extends IllegalStateException {
        private final int year;

        public CalendarMissingException(int year) {
            super("NO_CALENDAR:" + year);
            this.year = year;
        }

        public int year() {
            return year;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class YearData {
        public int year;
        public String source;
        public String sourceUrl;
        public String legalBasis;
        public String note;
        public List<String> legalHolidays = List.of();
        public List<String> makeupWorkdays = List.of();
        public List<String> makeupRestDays = List.of();
        public List<String> weekends = List.of();
    }

    private static final Map<Integer, YearData> YEARS = load();
    private static volatile Map<Integer, YearData> ACTIVE = Map.of();
    private static volatile YearLookup lookup = year -> null;

    @FunctionalInterface
    public interface YearLookup {
        YearData find(int year);
    }

    private OaOvertimeCalendar() {
    }

    public static void replaceActiveYears(Map<Integer, YearData> active) {
        Map<Integer, YearData> copy = new HashMap<>();
        if (active != null) {
            copy.putAll(active);
        }
        ACTIVE = Map.copyOf(copy);
    }

    public static void setLookup(YearLookup yearLookup) {
        lookup = yearLookup == null ? year -> null : yearLookup;
    }

    public static boolean hasYear(int year) {
        return yearData(year) != null;
    }

    public static YearData yearData(int year) {
        YearData fromDb = lookup.find(year);
        if (fromDb != null) {
            return fromDb;
        }
        YearData overlay = ACTIVE.get(year);
        return overlay != null ? overlay : YEARS.get(year);
    }

    public static Set<Integer> configuredYears() {
        return Collections.unmodifiableSet(YEARS.keySet());
    }

    public static DayKind kind(LocalDate day) {
        YearData data = yearData(day.getYear());
        if (data == null) {
            throw new CalendarMissingException(day.getYear());
        }
        return classify(data, day);
    }

    public static DayKind classify(YearData data, LocalDate day) {
        String iso = day.toString();
        if (data.legalHolidays.contains(iso)) {
            return DayKind.LEGAL_HOLIDAY;
        }
        if (data.makeupWorkdays.contains(iso)) {
            return DayKind.MAKEUP_WORKDAY;
        }
        if (data.makeupRestDays.contains(iso)) {
            return DayKind.MAKEUP_REST;
        }
        DayOfWeek dow = day.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return DayKind.WEEKEND;
        }
        return DayKind.WEEKDAY;
    }

    public static boolean allowed(LocalDate day) {
        DayKind k = kind(day);
        return k == DayKind.LEGAL_HOLIDAY || k == DayKind.WEEKEND;
    }

    public static boolean allowed(DayKind k) {
        return k == DayKind.LEGAL_HOLIDAY || k == DayKind.WEEKEND;
    }

    public static TreeSet<LocalDate> forbiddenDays(Iterable<LocalDate> days) {
        TreeSet<LocalDate> bad = new TreeSet<>();
        for (LocalDate day : days) {
            if (!allowed(day)) {
                bad.add(day);
            }
        }
        return bad;
    }

    static List<String> weekendsOf(YearData data) {
        List<String> weekends = new ArrayList<>();
        LocalDate cursor = LocalDate.of(data.year, 1, 1);
        LocalDate end = LocalDate.of(data.year, 12, 31);
        while (!cursor.isAfter(end)) {
            if (classify(data, cursor) == DayKind.WEEKEND) {
                weekends.add(cursor.toString());
            }
            cursor = cursor.plusDays(1);
        }
        return List.copyOf(weekends);
    }

    private static Map<Integer, YearData> load() {
        ObjectMapper mapper = new ObjectMapper();
        Map<Integer, YearData> map = new HashMap<>();
        try (InputStream in = OaOvertimeCalendar.class.getResourceAsStream("/oa/overtime-calendar-2026.json")) {
            if (in == null) {
                throw new IllegalStateException("missing overtime calendar 2026");
            }
            YearData data = mapper.readValue(in, YearData.class);
            data.weekends = weekendsOf(data);
            map.put(data.year, data);
        } catch (IOException ex) {
            throw new IllegalStateException("cannot read overtime calendar", ex);
        }
        return map;
    }

    static Set<String> legalHolidaySet(int year) {
        YearData data = yearData(year);
        if (data == null) {
            return Set.of();
        }
        return data.legalHolidays.stream().collect(Collectors.toUnmodifiableSet());
    }
}
