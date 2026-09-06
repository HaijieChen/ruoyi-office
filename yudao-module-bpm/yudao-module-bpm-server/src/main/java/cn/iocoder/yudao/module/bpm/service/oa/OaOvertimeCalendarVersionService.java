package cn.iocoder.yudao.module.bpm.service.oa;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOAOvertimeCalendarPageReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.oa.BpmOAOvertimeCalendarVersionDO;
import cn.iocoder.yudao.module.bpm.dal.mysql.oa.BpmOAOvertimeCalendarVersionMapper;
import cn.iocoder.yudao.module.system.api.notify.NotifyMessageSendApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySendSingleToUserReqDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.OA_OVERTIME_CALENDAR_VERIFY_DENIED;
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

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Clock clock = Clock.system(SHANGHAI);
    private HttpGet httpGet = OaOvertimeCalendarVersionService::defaultGet;
    private Long notifyUserId = 1L;

    public void setClock(Clock clock) {
        this.clock = clock;
    }

    public void setHttpGet(HttpGet httpGet) {
        this.httpGet = httpGet;
    }

    public void setNotifyUserId(Long notifyUserId) {
        this.notifyUserId = notifyUserId;
    }

    @FunctionalInterface
    public interface HttpGet {
        String get(String url) throws Exception;
    }

    @PostConstruct
    public void loadActiveIntoClassifier() {
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

    public void verifyAndEnable(Long id, Long userId, boolean enable) {
        if (userId == null) {
            throw exception(OA_OVERTIME_CALENDAR_VERIFY_DENIED);
        }
        BpmOAOvertimeCalendarVersionDO row = get(id);
        if (!enable) {
            row.setStatus(BpmOAOvertimeCalendarVersionDO.REJECTED);
            row.setVerifiedBy(userId);
            row.setVerifiedAt(LocalDateTime.now(clock));
            versionMapper.updateById(row);
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

    public String fetchDueYears() {
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

    public String fetchYear(int year) {
        String url = "https://www.gov.cn/zhengce/zhengceku/";
        try {
            String html = httpGet.get(url);
            if (html == null || html.isBlank()) {
                return recordFailure(year, url, "empty", "empty-body");
            }
            Optional<OaOvertimeCalendar.YearData> parsed = OaOvertimeCalendarNoticeParser.parse(year, html);
            if (parsed.isEmpty()) {
                if (!html.contains(year + "年部分节假日安排")) {
                    return recordNotPublished(year, url, html);
                }
                return recordFailure(year, url, "parse", "parse-incomplete");
            }
            return recordParsed(year, url, html, parsed.get());
        } catch (Exception ex) {
            return recordFailure(year, url, "fetch", "fetch-error");
        }
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
                .notifyFingerprint(sha256(toCanonical(data)))
                .build();
    }

    private void notifyOnce(BpmOAOvertimeCalendarVersionDO row, String event) {
        try {
            NotifySendSingleToUserReqDTO req = new NotifySendSingleToUserReqDTO();
            req.setUserId(notifyUserId);
            req.setTemplateCode(NOTIFY_TEMPLATE);
            req.setTemplateParams(Map.of(
                    "year", String.valueOf(row.getCalendarYear()),
                    "event", event,
                    "status", row.getStatus()));
            notifyMessageSendApi.sendSingleMessageToAdmin(req);
        } catch (Exception ignored) {
            // template may be missing in early env
        }
    }

    private String diffAgainstActive(int year, OaOvertimeCalendar.YearData incoming) {
        BpmOAOvertimeCalendarVersionDO active = versionMapper.selectActiveByYear(year);
        if (active == null) {
            return "{\"added\":true}";
        }
        return "{\"from\":\"" + active.getContentHash() + "\",\"to\":\"" + sha256(toCanonical(incoming)) + "\"}";
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
                                .GET()
                                .build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString());
                return resp.body();
            } catch (Exception ex) {
                last = ex;
            }
        }
        throw last;
    }
}
