package cn.iocoder.yudao.module.bpm.service.definition;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDataSourceDO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDataSourceLogDO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDataSourceVersionDO;
import cn.iocoder.yudao.module.bpm.dal.mysql.definition.BpmFormDataSourceLogMapper;
import cn.iocoder.yudao.module.bpm.dal.mysql.definition.BpmFormDataSourceMapper;
import cn.iocoder.yudao.module.bpm.dal.mysql.definition.BpmFormDataSourceVersionMapper;
import cn.iocoder.yudao.module.bpm.framework.datasource.*;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.*;

/**
 * Published-source execution coordinator. Raw SQL, credentials, authorization and parameter values are never logged.
 */
@Service
@Slf4j
public class BpmFormDataSourceExecutionServiceImpl implements BpmFormDataSourceExecutionService {

    private static final String CACHE_PREFIX = "bpm:form-data-source:";
    private static final int MAX_REQUEST_KEYS = 50;
    private static final int MAX_REQUEST_KEY_LENGTH = 64;
    private static final int MAX_REQUEST_NESTING = 3;
    private static final int MAX_REQUEST_COLLECTION_SIZE = 200;
    private static final int MAX_REQUEST_STRING_LENGTH = 4096;
    private static final long MAX_REQUEST_APPROXIMATE_BYTES = 64L * 1024L;
    private static final Pattern SAFE_REQUEST_KEY = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,63}$");
    private static final Set<String> RESERVED_PARAMS = Set.of("tenantId", "userId", "deptId", "companyId");

    private final BpmFormDataSourceMapper dataSourceMapper;
    private final BpmFormDataSourceVersionMapper versionMapper;
    private final BpmFormDataSourceLogMapper logMapper;
    private final BpmFormDataSourceContextResolver contextResolver;
    private final Map<Integer, BpmFormDataSourceProvider> providers;
    private final StringRedisTemplate redisTemplate;
    private final BpmFormDataSourceProperties properties;

    public BpmFormDataSourceExecutionServiceImpl(BpmFormDataSourceMapper dataSourceMapper,
                                                 BpmFormDataSourceVersionMapper versionMapper,
                                                 BpmFormDataSourceLogMapper logMapper,
                                                 BpmFormDataSourceContextResolver contextResolver,
                                                 List<BpmFormDataSourceProvider> providers,
                                                 StringRedisTemplate redisTemplate,
                                                 BpmFormDataSourceProperties properties) {
        this.dataSourceMapper = dataSourceMapper;
        this.versionMapper = versionMapper;
        this.logMapper = logMapper;
        this.contextResolver = contextResolver;
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
                BpmFormDataSourceProvider::getType, Function.identity()));
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public BpmFormDataSourceQueryResult execute(String code, Map<String, Object> requestParameters,
                                                LoginUser loginUser, String authorization,
                                                Long formId, String processInstanceId) {
        BpmFormDataSourceDO source = dataSourceMapper.selectByCode(code);
        if (source == null || !CommonStatusEnum.isEnable(source.getStatus()) || source.getPublishedVersion() == null) {
            throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_UNPUBLISHED);
        }
        BpmFormDataSourceVersionDO version = versionMapper.selectPublished(source.getId());
        if (version == null || !Objects.equals(source.getPublishedVersion(), version.getVersion())) {
            throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_UNPUBLISHED);
        }
        return executeVersion(source, version, requestParameters, loginUser, authorization,
                formId, processInstanceId, true);
    }

    @Override
    public BpmFormDataSourceQueryResult executeVersion(BpmFormDataSourceDO source,
                                                       BpmFormDataSourceVersionDO version,
                                                       Map<String, Object> requestParameters,
                                                       LoginUser loginUser, String authorization,
                                                       Long formId, String processInstanceId,
                                                       boolean cacheEnabled) {
        if (source == null || version == null || source.getId() == null
                || !Objects.equals(source.getId(), version.getDataSourceId())) {
            throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_CONFIG_INVALID);
        }
        LoginUser effectiveUser = effectiveUser(loginUser);
        if (effectiveUser == null) {
            throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_EXECUTION_FAILED);
        }

        String parameterDigest = digest(Map.of());
        long startedAt = System.nanoTime();
        try {
            List<SchemaField> parameterFields = parseSchema(version.getParameterSchema());
            validateRequestParameters(requestParameters, parameterFields);
            parameterDigest = digest(requestParameters == null ? Map.of() : requestParameters);
            Map<String, Object> parameters = contextResolver.resolve(requestParameters, effectiveUser);
            parameterDigest = digest(parameters);
            validateParameters(parameterFields, parameters);
            BpmFormDataSourceProvider provider = providers.get(source.getType());
            if (provider == null) {
                throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_CONFIG_INVALID);
            }

            String cacheKey = null;
            if (cacheEnabled) {
                boolean userScoped = isUserScoped(source, version.getSourceConfig(), parameterFields);
                Map<String, Object> cacheParameters = new LinkedHashMap<>(parameters);
                if (!userScoped) {
                    cacheParameters.remove("userId");
                }
                cacheKey = buildCacheKey(effectiveUser, source, version, digest(cacheParameters), userScoped);
                BpmFormDataSourceQueryResult cached = getCached(cacheKey, version.getCacheSeconds());
                if (cached != null) {
                    saveAudit(source, version, effectiveUser, formId, processInstanceId, parameterDigest,
                            cached.getRows().size(), elapsedMs(startedAt), true, null);
                    return cached;
                }
            }

            BpmFormDataSourceExecutionContext context = new BpmFormDataSourceExecutionContext(
                    source, version, Collections.unmodifiableMap(new LinkedHashMap<>(parameters)), authorization,
                    effectiveUser.getTenantId(), effectiveUser.getId());
            BpmFormDataSourceQueryResult providerResult = provider.execute(context);
            BpmFormDataSourceQueryResult safeResult = validateCopyAndMask(providerResult, version);
            if (cacheEnabled) {
                cache(cacheKey, version.getCacheSeconds(), safeResult);
            }
            saveAudit(source, version, effectiveUser, formId, processInstanceId, parameterDigest,
                    safeResult.getRows().size(), elapsedMs(startedAt), true, null);
            return safeResult;
        } catch (ServiceException ex) {
            saveAudit(source, version, effectiveUser, formId, processInstanceId, parameterDigest,
                    null, elapsedMs(startedAt), false, String.valueOf(ex.getCode()));
            throw ex;
        } catch (RuntimeException ex) {
            saveAudit(source, version, effectiveUser, formId, processInstanceId, parameterDigest,
                    null, elapsedMs(startedAt), false, String.valueOf(BPM_DATA_SOURCE_EXECUTION_FAILED.getCode()));
            throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_EXECUTION_FAILED);
        }
    }

    private BpmFormDataSourceQueryResult validateCopyAndMask(BpmFormDataSourceQueryResult result,
                                                              BpmFormDataSourceVersionDO version) {
        if (result == null || result.getRows() == null) {
            throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_RESULT_MAPPING_MISMATCH);
        }
        int maxRows = bounded(version.getMaxRows(), properties.getMaxRows());
        if (result.getRows().size() > maxRows) {
            throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_ROW_LIMIT);
        }
        List<SchemaField> resultFields = parseSchema(version.getResultSchema());
        List<Map<String, Object>> safeRows = new ArrayList<>(result.getRows().size());
        for (Map<String, Object> input : result.getRows()) {
            if (input == null) {
                throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_RESULT_MAPPING_MISMATCH);
            }
            Map<String, Object> row = resultFields.isEmpty() ? new LinkedHashMap<>(input) : new LinkedHashMap<>();
            for (SchemaField field : resultFields) {
                if (!input.containsKey(field.getName()) || !isCompatible(field.getType(), input.get(field.getName()))) {
                    throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_RESULT_MAPPING_MISMATCH);
                }
                row.put(field.getName(), input.get(field.getName()));
                String strategy = StringUtils.hasText(field.getMask()) ? field.getMask() : field.getMaskStrategy();
                if (StringUtils.hasText(strategy) && row.get(field.getName()) != null) {
                    row.put(field.getName(), mask(row.get(field.getName()), strategy));
                }
            }
            safeRows.add(row);
        }
        return new BpmFormDataSourceQueryResult(safeRows,
                result.getTotal() == null ? safeRows.size() : result.getTotal(), version.getVersion());
    }

    private static void validateParameters(List<SchemaField> fields, Map<String, Object> parameters) {
        for (SchemaField field : fields) {
            Object value = parameters.get(field.getName());
            if (Boolean.TRUE.equals(field.getRequired()) && value == null) {
                throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_PARAM_MISSING, field.getName());
            }
            if (value != null && !isCompatible(field.getType(), value)) {
                throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_PARAM_TYPE_MISMATCH, field.getName());
            }
        }
    }

    /**
     * Validates the untrusted request before any server context is injected or provider/cache work is performed.
     * Only top-level parameter names declared by the persisted schema are accepted. Reserved context names remain
     * server-owned even when they are declared in that schema.
     */
    private static void validateRequestParameters(Map<String, Object> requestParameters,
                                                  List<SchemaField> parameterFields) {
        if (requestParameters == null || requestParameters.isEmpty()) {
            return;
        }
        // Keep the existing, more specific error stable. It must win over unknown-key and size validation.
        for (Object key : requestParameters.keySet()) {
            if (key instanceof String name && RESERVED_PARAMS.contains(name)) {
                throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_PARAM_RESERVED, name);
            }
        }

        Set<String> declaredParameters = parameterFields.stream().map(SchemaField::getName)
                .collect(Collectors.toUnmodifiableSet());
        RequestShapeBudget budget = new RequestShapeBudget();
        IdentityHashMap<Object, Boolean> visiting = new IdentityHashMap<>();
        validateMapContents(requestParameters, 0, budget, visiting);
        for (Object key : requestParameters.keySet()) {
            if (!(key instanceof String name) || !declaredParameters.contains(name)) {
                throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_PARAM_INVALID);
            }
        }
    }

    private static void validateMapContents(Map<?, ?> map, int containerDepth, RequestShapeBudget budget,
                                            IdentityHashMap<Object, Boolean> visiting) {
        if (containerDepth > MAX_REQUEST_NESTING || map.size() > MAX_REQUEST_KEYS) {
            throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_PARAM_INVALID);
        }
        enterContainer(map, visiting);
        try {
            budget.addBytes(2); // braces
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key) || key.length() > MAX_REQUEST_KEY_LENGTH
                        || !SAFE_REQUEST_KEY.matcher(key).matches()) {
                    throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_PARAM_INVALID);
                }
                budget.addKey();
                budget.addBytes(utf8Length(key) + 4L); // quotes, colon, separator
                validateRequestValue(entry.getValue(), containerDepth + 1, budget, visiting);
            }
        } finally {
            visiting.remove(map);
        }
    }

    private static void validateRequestValue(Object value, int containerDepth, RequestShapeBudget budget,
                                             IdentityHashMap<Object, Boolean> visiting) {
        if (value == null) {
            budget.addBytes(4);
            return;
        }
        if (value instanceof String string) {
            if (string.length() > MAX_REQUEST_STRING_LENGTH) {
                throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_PARAM_INVALID);
            }
            budget.addBytes(utf8Length(string) + 2L);
            return;
        }
        if (value instanceof Boolean) {
            budget.addBytes(5);
            return;
        }
        if (isSafeNumber(value)) {
            if (value instanceof BigInteger integer && integer.bitLength() > 220_000
                    || value instanceof BigDecimal decimal && decimal.precision() > 65_536) {
                throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_PARAM_INVALID);
            }
            String encodedNumber = String.valueOf(value);
            if (encodedNumber.length() > MAX_REQUEST_STRING_LENGTH) {
                throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_PARAM_INVALID);
            }
            budget.addBytes(encodedNumber.length());
            return;
        }
        if (value instanceof LocalDate || value instanceof LocalDateTime) {
            budget.addBytes(String.valueOf(value).length() + 2L);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            validateMapContents(map, containerDepth, budget, visiting);
            return;
        }
        if (value instanceof Collection<?> collection) {
            if (containerDepth > MAX_REQUEST_NESTING || collection.size() > MAX_REQUEST_COLLECTION_SIZE) {
                throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_PARAM_INVALID);
            }
            enterContainer(collection, visiting);
            try {
                budget.addBytes(2); // brackets
                for (Object item : collection) {
                    budget.addBytes(1); // separator
                    validateRequestValue(item, containerDepth + 1, budget, visiting);
                }
            } finally {
                visiting.remove(collection);
            }
            return;
        }
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            if (containerDepth > MAX_REQUEST_NESTING || length > MAX_REQUEST_COLLECTION_SIZE) {
                throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_PARAM_INVALID);
            }
            enterContainer(value, visiting);
            try {
                budget.addBytes(2);
                for (int index = 0; index < length; index++) {
                    budget.addBytes(1);
                    validateRequestValue(java.lang.reflect.Array.get(value, index),
                            containerDepth + 1, budget, visiting);
                }
            } finally {
                visiting.remove(value);
            }
            return;
        }
        throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_PARAM_INVALID);
    }

    private static void enterContainer(Object container, IdentityHashMap<Object, Boolean> visiting) {
        if (visiting.put(container, Boolean.TRUE) != null) {
            throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_PARAM_INVALID);
        }
    }

    private static boolean isSafeNumber(Object value) {
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
                || value instanceof Float || value instanceof Double || value instanceof BigInteger
                || value instanceof BigDecimal)) {
            return false;
        }
        if (value instanceof Float number) {
            return Float.isFinite(number);
        }
        if (value instanceof Double number) {
            return Double.isFinite(number);
        }
        return true;
    }

    private static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static final class RequestShapeBudget {
        private int keys;
        private long approximateBytes;

        void addKey() {
            if (++keys > MAX_REQUEST_KEYS) {
                throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_PARAM_INVALID);
            }
        }

        void addBytes(long bytes) {
            approximateBytes += bytes;
            if (approximateBytes > MAX_REQUEST_APPROXIMATE_BYTES) {
                throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_PARAM_INVALID);
            }
        }
    }

    private static boolean isCompatible(String type, Object value) {
        if (value == null || !StringUtils.hasText(type)) {
            return true;
        }
        try {
            return switch (type.toUpperCase(Locale.ROOT)) {
                case "STRING" -> value instanceof String;
                case "LONG", "INTEGER" -> value instanceof Byte || value instanceof Short
                        || value instanceof Integer || value instanceof Long
                        || value instanceof String string && string.matches("-?\\d+");
                case "DECIMAL", "NUMBER" -> value instanceof Number
                        || value instanceof String string && new BigDecimal(string) != null;
                case "BOOLEAN" -> value instanceof Boolean
                        || value instanceof String string && ("true".equalsIgnoreCase(string)
                        || "false".equalsIgnoreCase(string));
                case "DATE" -> value instanceof LocalDate
                        || value instanceof String string && LocalDate.parse(string) != null;
                case "DATETIME" -> value instanceof LocalDateTime
                        || value instanceof String string && LocalDateTime.parse(string) != null;
                default -> false;
            };
        } catch (NumberFormatException | DateTimeParseException ex) {
            return false;
        }
    }

    private BpmFormDataSourceQueryResult getCached(String key, Integer cacheSeconds) {
        if (cacheSeconds == null || cacheSeconds <= 0) {
            return null;
        }
        String json = redisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return JsonUtils.parseObject(json, BpmFormDataSourceQueryResult.class);
        } catch (RuntimeException ex) {
            redisTemplate.delete(key);
            return null;
        }
    }

    private void cache(String key, Integer cacheSeconds, BpmFormDataSourceQueryResult result) {
        if (cacheSeconds == null || cacheSeconds <= 0) {
            return;
        }
        redisTemplate.opsForValue().set(key, JsonUtils.toJsonString(result), Duration.ofSeconds(cacheSeconds));
    }

    private static String buildCacheKey(LoginUser user, BpmFormDataSourceDO source,
                                        BpmFormDataSourceVersionDO version, String parameterDigest,
                                        boolean userScoped) {
        return CACHE_PREFIX + user.getTenantId() + ":" + source.getCode() + ":" + version.getVersion()
                + ":" + (userScoped ? user.getId() : "shared") + ":" + parameterDigest;
    }

    private static boolean isUserScoped(BpmFormDataSourceDO source, String sourceConfig,
                                        List<SchemaField> parameterFields) {
        if (Objects.equals(source.getType(), BpmFormDataSourceProvider.TYPE_PLATFORM_API)) {
            return true;
        }
        if (parameterFields.stream().anyMatch(field -> "userId".equals(field.getName()))) {
            return true;
        }
        Map<String, Object> config = JsonUtils.parseObjectQuietly(sourceConfig, new TypeReference<>() {});
        if (config == null) {
            throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_CONFIG_INVALID);
        }
        if (Boolean.TRUE.equals(config.get("userScoped"))) {
            return true;
        }
        return Objects.equals(source.getType(), BpmFormDataSourceProvider.TYPE_SQL)
                && config.get("sql") instanceof String sql
                && BpmFormDataSourceExecutor.referencesNamedParameter(sql, "userId");
    }

    private void saveAudit(BpmFormDataSourceDO source, BpmFormDataSourceVersionDO version,
                           LoginUser user, Long formId, String processInstanceId, String parameterDigest,
                           Integer rowCount, long durationMs, boolean success, String errorCode) {
        BpmFormDataSourceLogDO audit = new BpmFormDataSourceLogDO()
                .setDataSourceId(source.getId()).setVersion(version.getVersion()).setFormId(formId)
                .setProcessInstanceId(processInstanceId).setUserId(user.getId())
                .setParameterDigest(parameterDigest).setRowCount(rowCount).setDurationMs(durationMs)
                .setSuccess(success).setErrorCode(errorCode);
        audit.setTenantId(user.getTenantId());
        try {
            logMapper.insert(audit);
        } catch (RuntimeException ignored) {
            // Never expose provider input, SQL, headers or the persistence exception itself.
            log.warn("[saveAudit][data-source audit insert failed, dataSourceId={}]", source.getId());
        }
    }

    private static List<SchemaField> parseSchema(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<SchemaField> fields = JsonUtils.parseArray(json, SchemaField.class);
            if (fields.stream().anyMatch(field -> !StringUtils.hasText(field.getName()))) {
                throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_CONFIG_INVALID);
            }
            return fields;
        } catch (ServiceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_CONFIG_INVALID);
        }
    }

    private static Object mask(Object value, String strategy) {
        String text = String.valueOf(value);
        return switch (strategy.toUpperCase(Locale.ROOT)) {
            case "FULL" -> "***";
            case "PHONE" -> text.length() >= 7
                    ? text.substring(0, 3) + "****" + text.substring(text.length() - 4) : "***";
            case "EMAIL" -> {
                int at = text.indexOf('@');
                yield at > 0 ? text.substring(0, 1) + "***" + text.substring(at) : "***";
            }
            case "ID_CARD", "BANK_CARD" -> text.length() > 4
                    ? "****" + text.substring(text.length() - 4) : "***";
            default -> throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_CONFIG_INVALID);
        };
    }

    static String digest(Map<String, Object> parameters) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical(parameters).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_EXECUTION_FAILED);
        }
    }

    private static String canonical(Object value) {
        if (value == null) {
            return "N";
        }
        if (value instanceof Map<?, ?> map) {
            List<String> entries = map.entrySet().stream()
                    .map(entry -> token(canonical(String.valueOf(entry.getKey()))) + token(canonical(entry.getValue())))
                    .sorted().toList();
            return "M" + entries.size() + entries.stream().map(BpmFormDataSourceExecutionServiceImpl::token)
                    .collect(Collectors.joining());
        }
        if (value instanceof Collection<?> collection) {
            return "L" + collection.size() + collection.stream()
                    .map(BpmFormDataSourceExecutionServiceImpl::canonical)
                    .map(BpmFormDataSourceExecutionServiceImpl::token).collect(Collectors.joining());
        }
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            StringBuilder encoded = new StringBuilder("A").append(token(value.getClass().getComponentType().getName()))
                    .append(length);
            for (int i = 0; i < length; i++) {
                encoded.append(token(canonical(java.lang.reflect.Array.get(value, i))));
            }
            return encoded.toString();
        }
        return "V" + token(value.getClass().getName()) + token(String.valueOf(value));
    }

    private static String token(String value) {
        return value.length() + ":" + value;
    }

    private static int bounded(Integer configured, int globalLimit) {
        int safeGlobal = Math.max(1, globalLimit);
        return configured == null ? safeGlobal : Math.max(1, Math.min(configured, safeGlobal));
    }

    private static LoginUser effectiveUser(LoginUser loginUser) {
        if (loginUser == null || loginUser.getId() == null) {
            return null;
        }
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            tenantId = loginUser.getTenantId();
        }
        if (tenantId == null) {
            return null;
        }
        return new LoginUser().setId(loginUser.getId()).setUserType(loginUser.getUserType())
                .setInfo(loginUser.getInfo()).setTenantId(tenantId).setScopes(loginUser.getScopes())
                .setExpiresTime(loginUser.getExpiresTime()).setContext(loginUser.getContext())
                .setVisitTenantId(loginUser.getVisitTenantId());
    }

    private static long elapsedMs(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    @Data
    public static class SchemaField {
        private String name;
        private String type;
        private Boolean required;
        private String mask;
        private String maskStrategy;
    }

}
