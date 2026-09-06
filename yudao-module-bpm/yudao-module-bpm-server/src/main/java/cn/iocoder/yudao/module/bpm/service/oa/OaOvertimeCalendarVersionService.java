package cn.iocoder.yudao.module.bpm.service.oa;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOAOvertimeCalendarPageReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.oa.BpmOAOvertimeCalendarVersionDO;
import cn.iocoder.yudao.module.bpm.dal.mysql.oa.BpmOAOvertimeCalendarVersionMapper;
import cn.iocoder.yudao.module.system.api.notify.NotifyMessageSendApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySendSingleToUserReqDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.RoleApi;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.OA_OVERTIME_CALENDAR_VERIFY_DENIED;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.OA_OVERTIME_CALENDAR_VERSION_NOT_PENDING;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.OA_OVERTIME_NOT_EXISTS;

@Service
@Validated
@Slf4j
public class OaOvertimeCalendarVersionService {

    public static final String NOTIFY_TEMPLATE = "bpm_oa_overtime_calendar";
    public static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    public static final Set<String> ALLOWED_HOSTS = Set.of("www.gov.cn");

    @Resource
    private BpmOAOvertimeCalendarVersionMapper versionMapper;

    @Resource
    private NotifyMessageSendApi notifyMessageSendApi;

    @Resource
    private RoleApi roleApi;

    @Resource
    private PermissionApi permissionApi;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Clock clock = Clock.system(SHANGHAI);
    private HttpGet httpGet = OaOvertimeCalendarVersionService::defaultGet;
    private List<Long> notifyUserIds = List.of();

    public void setClock(Clock clock) {
        this.clock = clock;
    }

    public void setHttpGet(HttpGet httpGet) {
        this.httpGet = httpGet;
    }

    public void setNotifyUserIds(List<Long> notifyUserIds) {
        this.notifyUserIds = notifyUserIds == null ? List.of() : List.copyOf(notifyUserIds);
    }

    @FunctionalInterface
    public interface HttpGet {
        String get(String url) throws Exception;
    }

    @PostConstruct
    public void loadActiveIntoClassifier() {
        OaOvertimeCalendar.setLookup(year -> {
            try {
                BpmOAOvertimeCalendarVersionDO row = versionMapper.selectActiveByYear(year);
                return row == null ? null : toYearData(row);
            } catch (Exception ex) {
                log.warn("[overtime-calendar] db lookup skipped: {}", ex.getMessage());
                return null;
            }
        });
        try {
            Map<Integer, OaOvertimeCalendar.YearData> active = new LinkedHashMap<>();
            List<BpmOAOvertimeCalendarVersionDO> rows = versionMapper.selectList(
                    BpmOAOvertimeCalendarVersionDO::getStatus, BpmOAOvertimeCalendarVersionDO.ACTIVE);
            for (BpmOAOvertimeCalendarVersionDO row : rows) {
                active.put(row.getCalendarYear(), toYearData(row));
            }
            OaOvertimeCalendar.replaceActiveYears(active);
        } catch (Exception ex) {
            log.warn("[overtime-calendar] skip overlay load: {}", ex.getMessage());
        }
    }

    public PageResult<BpmOAOvertimeCalendarVersionDO> getPage(BpmOAOvertimeCalendarPageReqVO reqVO) {
        return versionMapper.selectPage(reqVO);
    }

    public BpmOAOvertimeCalendarVersionDO get(Long id) {
        BpmOAOvertimeCalendarVersionDO row = versionMapper.selectById(id);
        if (row == null) {
            throw exception(OA_OVERTIME_NOT_EXISTS);
        }
        return row;
    }

    @Transactional(rollbackFor = Exception.class)
    public void verifyAndEnable(Long id, Long userId, boolean enable) {
        if (userId == null) {
            throw exception(OA_OVERTIME_CALENDAR_VERIFY_DENIED);
        }
        BpmOAOvertimeCalendarVersionDO row = versionMapper.selectByIdForUpdate(id);
        if (row == null) {
            row = get(id);
        }
        if (!BpmOAOvertimeCalendarVersionDO.PENDING.equals(row.getStatus()) || !isComplete(toYearData(row))) {
            throw exception(OA_OVERTIME_CALENDAR_VERSION_NOT_PENDING);
        }
        if (!enable) {
            row.setStatus(BpmOAOvertimeCalendarVersionDO.REJECTED);
            row.setVerifiedBy(userId);
            row.setVerifiedAt(LocalDateTime.now(clock));
            versionMapper.updateById(row);
            loadActiveIntoClassifier();
            return;
        }
        BpmOAOvertimeCalendarVersionDO current = versionMapper.selectActiveByYear(row.getCalendarYear());
        if (current != null && !current.getId().equals(row.getId())) {
            current.setStatus(BpmOAOvertimeCalendarVersionDO.REJECTED);
            versionMapper.updateById(current);
        }
        row.setStatus(BpmOAOvertimeCalendarVersionDO.ACTIVE);
        row.setVerifiedBy(userId);
        row.setVerifiedAt(LocalDateTime.now(clock));
        versionMapper.updateById(row);
        loadActiveIntoClassifier();
    }

    public synchronized String fetchDueYears() {
        LocalDate today = LocalDate.now(clock);
        StringBuilder out = new StringBuilder();
        for (Integer year : OaOvertimeCalendarSchedule.targetYears(today)) {
            out.append(year).append('=').append(fetchYear(year)).append(';');
        }
        if (out.isEmpty()) {
            return "skip";
        }
        return out.toString();
    }

    public synchronized String fetchYear(int year) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        BpmOAOvertimeCalendarVersionDO active = versionMapper.selectActiveByYear(year);
        if (active != null && active.getSourceUrl() != null) {
            candidates.add(active.getSourceUrl());
        }
        boolean listingOk = false;
        boolean listingLooksOfficial = false;
        Set<Integer> listingYears = new java.util.HashSet<>();
        String lastListingUrl = OaOvertimeCalendarNoticeLocator.LISTING_URLS.get(0);
        Exception lastListingError = null;
        for (String listingUrl : OaOvertimeCalendarNoticeLocator.LISTING_URLS) {
            lastListingUrl = listingUrl;
            String listingHtml;
            try {
                listingHtml = httpGet.get(listingUrl);
            } catch (Exception ex) {
                lastListingError = ex;
                log.warn("[overtime-calendar] listing {} failed: {}", listingUrl, failureNote("listing-fetch", ex));
                continue;
            }
            if (listingHtml == null || listingHtml.isBlank()) {
                lastListingError = new IllegalStateException("empty body");
                continue;
            }
            listingOk = true;
            listingLooksOfficial = listingLooksOfficial
                    || OaOvertimeCalendarNoticeLocator.looksLikePolicyListing(listingHtml);
            listingYears.addAll(OaOvertimeCalendarNoticeLocator.noticeYears(listingHtml));
            candidates.addAll(OaOvertimeCalendarNoticeLocator.noticeUrls(year, listingHtml, listingUrl));
        }
        if (candidates.isEmpty()) {
            if (!listingOk) {
                return recordFailure(year, lastListingUrl,
                        failureNote("listing-fetch", lastListingError), "listing-fetch-error");
            }
            if (!listingLooksOfficial) {
                return recordFailure(year, lastListingUrl, "listing-format", "listing-format-change");
            }
            if (!listingYears.isEmpty() && !listingYears.contains(year)) {
                return recordNotPublished(year, lastListingUrl, "years=" + listingYears);
            }
            return recordFailure(year, lastListingUrl, "listing-no-link", "listing-no-link");
        }
        Exception last = null;
        for (String url : candidates) {
            try {
                String html = httpGet.get(url);
                if (html == null || html.isBlank()) {
                    last = new IllegalStateException("empty body");
                    log.warn("[overtime-calendar] notice {} empty body", url);
                    continue;
                }
                Optional<OaOvertimeCalendar.YearData> parsed = OaOvertimeCalendarNoticeParser.parse(year, html);
                if (parsed.isEmpty()) {
                    last = new IllegalStateException("parse-incomplete");
                    log.warn("[overtime-calendar] notice {} parse-incomplete", url);
                    continue;
                }
                return recordParsed(year, url, html, parsed.get());
            } catch (Exception ex) {
                last = ex;
                log.warn("[overtime-calendar] notice {} failed: {}", url, failureNote("notice-fetch", ex));
            }
        }
        return recordFailure(year, candidates.iterator().next(),
                failureNote("notice-fetch", last), "notice-fetch-error");
    }

    public BpmOAOvertimeCalendarVersionDO importSeedActive(OaOvertimeCalendar.YearData data, String url) {
        BpmOAOvertimeCalendarVersionDO row = newRow(data, url, "", BpmOAOvertimeCalendarVersionDO.ACTIVE);
        versionMapper.insert(row);
        loadActiveIntoClassifier();
        return row;
    }

    private String recordParsed(int year, String url, String html, OaOvertimeCalendar.YearData data) {
        String hash = sha256(toCanonical(data));
        BpmOAOvertimeCalendarVersionDO existing = versionMapper.selectByYearAndHash(year, hash);
        if (existing != null) {
            enrichMetadata(existing, data);
            return "same:" + existing.getId();
        }
        BpmOAOvertimeCalendarVersionDO row = newRow(data, url, excerpt(html), BpmOAOvertimeCalendarVersionDO.PENDING);
        row.setContentHash(hash);
        row.setDiffJson(diffAgainstActive(year, data));
        versionMapper.insert(row);
        notifyOnce(row, "pending");
        return "pending:" + row.getId();
    }

    private String recordFailure(int year, String url, String note, String fingerprint) {
        BpmOAOvertimeCalendarVersionDO last = versionMapper.selectActiveByYear(year);
        BpmOAOvertimeCalendarVersionDO row = BpmOAOvertimeCalendarVersionDO.builder()
                .calendarYear(year)
                .sourceUrl(url)
                .fetchedAt(LocalDateTime.now(clock))
                .contentHash("fail-" + fingerprint + "-" + year)
                .status(BpmOAOvertimeCalendarVersionDO.FAILED)
                .parseNote(note)
                .notifyFingerprint(fingerprint + ":" + year)
                .build();
        BpmOAOvertimeCalendarVersionDO dup = versionMapper.selectByYearAndHash(year, row.getContentHash());
        if (dup != null) {
            return "fail-dup";
        }
        versionMapper.insert(row);
        notifyOnce(row, "failed");
        return last == null ? "failed" : "failed-kept-active";
    }

    private String recordNotPublished(int year, String url, String html) {
        String hash = "not-published-" + year;
        if (versionMapper.selectByYearAndHash(year, hash) != null) {
            return "not-published-dup";
        }
        BpmOAOvertimeCalendarVersionDO row = BpmOAOvertimeCalendarVersionDO.builder()
                .calendarYear(year)
                .sourceUrl(url)
                .fetchedAt(LocalDateTime.now(clock))
                .contentHash(hash)
                .status(BpmOAOvertimeCalendarVersionDO.NOT_PUBLISHED)
                .rawExcerpt(excerpt(html))
                .parseNote("next-year-not-published")
                .notifyFingerprint("not-published:" + year)
                .build();
        versionMapper.insert(row);
        notifyOnce(row, "not_published");
        return "not-published";
    }

    private BpmOAOvertimeCalendarVersionDO newRow(OaOvertimeCalendar.YearData data, String url,
                                                  String excerpt, String status) {
        return BpmOAOvertimeCalendarVersionDO.builder()
                .calendarYear(data.year)
                .source(data.source)
                .sourceUrl(url)
                .fetchedAt(LocalDateTime.now(clock))
                .contentHash(sha256(toCanonical(data)))
                .status(status)
                .rawExcerpt(excerpt)
                .legalHolidaysJson(json(data.legalHolidays))
                .makeupWorkdaysJson(json(data.makeupWorkdays))
                .makeupRestDaysJson(json(data.makeupRestDays))
                .weekendsJson(json(data.weekends))
                .festivalsJson(json(data.festivals))
                .notifyFingerprint(sha256(toCanonical(data)))
                .build();
    }

    private void notifyOnce(BpmOAOvertimeCalendarVersionDO row, String event) {
        for (Long userId : resolveNotifyUserIds()) {
            try {
                NotifySendSingleToUserReqDTO req = new NotifySendSingleToUserReqDTO();
                req.setUserId(userId);
                req.setTemplateCode(NOTIFY_TEMPLATE);
                req.setTemplateParams(Map.of(
                        "year", String.valueOf(row.getCalendarYear()),
                        "event", event,
                        "status", row.getStatus()));
                notifyMessageSendApi.sendSingleMessageToAdmin(req);
            } catch (Exception ex) {
                log.warn("[overtime-calendar] notify userId={} failed: {}", userId, ex.getMessage());
            }
        }
    }

    private List<Long> resolveNotifyUserIds() {
        if (notifyUserIds != null && !notifyUserIds.isEmpty()) {
            return notifyUserIds;
        }
        if (roleApi == null || permissionApi == null) {
            return List.of();
        }
        try {
            List<Long> roleIds = roleApi.getRoleIdListByCodes(List.of("hr_admin")).getCheckedData();
            if (roleIds == null || roleIds.isEmpty()) {
                log.warn("[overtime-calendar] no hr_admin role ids");
                return List.of();
            }
            Set<Long> userIds = permissionApi.getUserRoleIdListByRoleIds(roleIds).getCheckedData();
            if (userIds == null || userIds.isEmpty()) {
                log.warn("[overtime-calendar] hr_admin role has no users");
                return List.of();
            }
            log.info("[overtime-calendar] notify userIds={}", userIds);
            return List.copyOf(userIds);
        } catch (Exception ex) {
            log.warn("[overtime-calendar] resolve hr_admin userIds failed: {}: {}",
                    ex.getClass().getName(), ex.getMessage(), ex);
            return List.of();
        }
    }

    static String failureNote(String prefix, Exception last) {
        if (last == null) {
            return prefix;
        }
        String message = last.getMessage();
        if (message == null || message.isBlank()) {
            return prefix + ":" + last.getClass().getSimpleName();
        }
        return prefix + ":" + message;
    }

    /** Fill festivals and readable date diff without changing dates, hash, or status. */
    void enrichMetadata(BpmOAOvertimeCalendarVersionDO row, OaOvertimeCalendar.YearData data) {
        if (row == null || data == null) {
            return;
        }
        boolean missingFestivals = row.getFestivalsJson() == null || row.getFestivalsJson().isBlank()
                || "{}".equals(row.getFestivalsJson());
        boolean hashOnlyDiff = row.getDiffJson() == null || row.getDiffJson().contains("\"from\"")
                && row.getDiffJson().contains("\"to\"") && !row.getDiffJson().contains("legalHolidays");
        if (!missingFestivals && !hashOnlyDiff) {
            return;
        }
        if (missingFestivals && data.festivals != null && !data.festivals.isEmpty()) {
            row.setFestivalsJson(json(data.festivals));
        }
        if (hashOnlyDiff) {
            row.setDiffJson(diffAgainstActive(row.getCalendarYear(), data));
        }
        versionMapper.updateById(row);
    }

    private String diffAgainstActive(int year, OaOvertimeCalendar.YearData incoming) {
        BpmOAOvertimeCalendarVersionDO active = versionMapper.selectActiveByYear(year);
        if (active == null) {
            return json(Map.of("added", true,
                    "legalHolidays", Map.of("added", incoming.legalHolidays, "removed", List.of()),
                    "note", "无已生效版本"));
        }
        OaOvertimeCalendar.YearData current = toYearData(active);
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("legalHolidays", listDiff(current.legalHolidays, incoming.legalHolidays));
        diff.put("makeupWorkdays", listDiff(current.makeupWorkdays, incoming.makeupWorkdays));
        diff.put("makeupRestDays", listDiff(current.makeupRestDays, incoming.makeupRestDays));
        diff.put("weekends", listDiff(current.weekends, incoming.weekends));
        boolean changed = ((List<?>) ((Map<?, ?>) diff.get("legalHolidays")).get("added")).size()
                + ((List<?>) ((Map<?, ?>) diff.get("legalHolidays")).get("removed")).size()
                + ((List<?>) ((Map<?, ?>) diff.get("makeupWorkdays")).get("added")).size()
                + ((List<?>) ((Map<?, ?>) diff.get("makeupWorkdays")).get("removed")).size()
                + ((List<?>) ((Map<?, ?>) diff.get("makeupRestDays")).get("added")).size()
                + ((List<?>) ((Map<?, ?>) diff.get("makeupRestDays")).get("removed")).size()
                + ((List<?>) ((Map<?, ?>) diff.get("weekends")).get("added")).size()
                + ((List<?>) ((Map<?, ?>) diff.get("weekends")).get("removed")).size() > 0;
        diff.put("unchangedDates", !changed);
        diff.put("note", changed ? "日期或分类有变化" : "日期分类与生效版相同（指纹不同不代表业务日期变化）");
        return json(diff);
    }

    private static Map<String, List<String>> listDiff(List<String> from, List<String> to) {
        Set<String> oldSet = new java.util.TreeSet<>(from == null ? List.of() : from);
        Set<String> newSet = new java.util.TreeSet<>(to == null ? List.of() : to);
        List<String> added = newSet.stream().filter(d -> !oldSet.contains(d)).toList();
        List<String> removed = oldSet.stream().filter(d -> !newSet.contains(d)).toList();
        return Map.of("added", added, "removed", removed);
    }

    static boolean isComplete(OaOvertimeCalendar.YearData data) {
        return data != null
                && data.legalHolidays != null && data.legalHolidays.size() == 13
                && data.makeupWorkdays != null
                && data.makeupRestDays != null;
    }

    private OaOvertimeCalendar.YearData toYearData(BpmOAOvertimeCalendarVersionDO row) {
        OaOvertimeCalendar.YearData data = new OaOvertimeCalendar.YearData();
        data.year = row.getCalendarYear();
        data.source = row.getSource();
        data.sourceUrl = row.getSourceUrl();
        data.legalHolidays = readList(row.getLegalHolidaysJson());
        data.makeupWorkdays = readList(row.getMakeupWorkdaysJson());
        data.makeupRestDays = readList(row.getMakeupRestDaysJson());
        data.weekends = readList(row.getWeekendsJson());
        data.festivals = readMap(row.getFestivalsJson());
        return data;
    }

    @SuppressWarnings("unchecked")
    private List<String> readList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, List.class);
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(json, Map.class);
            Map<String, String> out = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : raw.entrySet()) {
                if (e.getValue() != null) {
                    out.put(e.getKey(), String.valueOf(e.getValue()));
                }
            }
            return out;
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private String toCanonical(OaOvertimeCalendar.YearData data) {
        return data.year + "|" + String.join(",", data.legalHolidays)
                + "|" + String.join(",", data.makeupWorkdays)
                + "|" + String.join(",", data.makeupRestDays);
    }

    private static String excerpt(String html) {
        String text = html.replaceAll("<[^>]+>", " ");
        return text.length() > 2000 ? text.substring(0, 2000) : text;
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String defaultGet(String url) throws Exception {
        java.net.URI uri = java.net.URI.create(url);
        if (!ALLOWED_HOSTS.contains(uri.getHost())) {
            throw new IllegalArgumentException("source host not allowed");
        }
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();
        Exception last = null;
        for (int i = 0; i < 3; i++) {
            try {
                java.net.http.HttpResponse<String> resp = client.send(
                        java.net.http.HttpRequest.newBuilder(uri)
                                .timeout(java.time.Duration.ofSeconds(10))
                                .header("User-Agent", "Mozilla/5.0 (compatible; OAOvertimeCalendar/1.0)")
                                .header("Accept", "text/html,application/xhtml+xml")
                                .GET()
                                .build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                    throw new IllegalStateException("http " + resp.statusCode());
                }
                return resp.body();
            } catch (Exception ex) {
                last = ex;
            }
        }
        throw last;
    }
}
