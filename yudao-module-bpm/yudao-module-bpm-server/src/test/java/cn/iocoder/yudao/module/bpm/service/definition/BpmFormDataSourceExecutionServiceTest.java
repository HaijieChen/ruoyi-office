package cn.iocoder.yudao.module.bpm.service.definition;

import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDataSourceDO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDataSourceLogDO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDataSourceVersionDO;
import cn.iocoder.yudao.module.bpm.dal.mysql.definition.BpmFormDataSourceLogMapper;
import cn.iocoder.yudao.module.bpm.dal.mysql.definition.BpmFormDataSourceMapper;
import cn.iocoder.yudao.module.bpm.dal.mysql.definition.BpmFormDataSourceVersionMapper;
import cn.iocoder.yudao.module.bpm.framework.datasource.*;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BpmFormDataSourceExecutionServiceTest {

    @Mock
    private BpmFormDataSourceMapper dataSourceMapper;
    @Mock
    private BpmFormDataSourceVersionMapper versionMapper;
    @Mock
    private BpmFormDataSourceLogMapper logMapper;
    @Mock
    private BpmFormDataSourceProvider provider;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private BpmFormDataSourceExecutionServiceImpl service;
    private BpmFormDataSourceProperties properties;

    @BeforeEach
    void setUp() {
        when(provider.getType()).thenReturn(BpmFormDataSourceProvider.TYPE_SQL);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.get(anyString())).thenAnswer(invocation -> cache.get(invocation.getArgument(0)));
        lenient().doAnswer(invocation -> {
            cache.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(java.time.Duration.class));
        properties = new BpmFormDataSourceProperties();
        service = new BpmFormDataSourceExecutionServiceImpl(dataSourceMapper, versionMapper, logMapper,
                new BpmFormDataSourceContextResolver(), List.of(provider), redisTemplate, properties);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void execute_usesActiveTenantContextConsistentlyWhenItDiffersFromLoginTenant() {
        mockPublishedSource("[]", "[]", true, 60);
        when(provider.execute(any())).thenReturn(new BpmFormDataSourceQueryResult(List.of(Map.of("id", 1L)), 1, 1));
        TenantContextHolder.setTenantId(9L);

        service.execute("employees", Map.of(), loginUser(1L, 7L), "Bearer token", null, null);

        ArgumentCaptor<BpmFormDataSourceExecutionContext> contextCaptor =
                ArgumentCaptor.forClass(BpmFormDataSourceExecutionContext.class);
        verify(provider).execute(contextCaptor.capture());
        assertEquals(9L, contextCaptor.getValue().getTenantId());
        assertEquals(9L, contextCaptor.getValue().getParameters().get("tenantId"));
        verify(logMapper).insert(argThat((BpmFormDataSourceLogDO log) -> Objects.equals(9L, log.getTenantId())));
        assertTrue(cache.keySet().stream().allMatch(key -> key.startsWith("bpm:form-data-source:9:")));
    }

    @Test
    void execute_sqlReferencingUserIdIsUserScopedEvenWithoutExplicitFlag() {
        mockPublishedSourceWithConfig("[]", "[]",
                "{\"sql\":\"SELECT id FROM employee WHERE user_id = :userId\",\"userScoped\":false}", 60);
        when(provider.execute(any())).thenReturn(new BpmFormDataSourceQueryResult(List.of(Map.of("id", 1L)), 1, 1));

        service.execute("employees", Map.of(), loginUser(1L, 7L), null, null, null);
        service.execute("employees", Map.of(), loginUser(1L, 8L), null, null, null);

        verify(provider, times(2)).execute(any());
        assertEquals(2, cache.size());
    }

    @Test
    void execute_platformApiIsUserScopedByDefault() {
        BpmFormDataSourceProvider apiProvider = mock(BpmFormDataSourceProvider.class);
        when(apiProvider.getType()).thenReturn(BpmFormDataSourceProvider.TYPE_PLATFORM_API);
        BpmFormDataSourceExecutionServiceImpl apiService = new BpmFormDataSourceExecutionServiceImpl(
                dataSourceMapper, versionMapper, logMapper, new BpmFormDataSourceContextResolver(),
                List.of(apiProvider), redisTemplate, properties);
        BpmFormDataSourceDO source = source().setType(BpmFormDataSourceProvider.TYPE_PLATFORM_API);
        BpmFormDataSourceVersionDO version = version("[]", "[]",
                sourceConfig("/admin-api/system/dept/simple-list", "GET"), 60);
        when(dataSourceMapper.selectByCode("employees")).thenReturn(source);
        when(versionMapper.selectPublished(10L)).thenReturn(version);
        when(apiProvider.execute(any())).thenReturn(
                new BpmFormDataSourceQueryResult(List.of(Map.of("id", 1L)), 1, 1));

        apiService.execute("employees", Map.of(), loginUser(1L, 7L), null, null, null);
        apiService.execute("employees", Map.of(), loginUser(1L, 8L), null, null, null);

        verify(apiProvider, times(2)).execute(any());
        assertEquals(2, cache.size());
    }

    @Test
    void digest_lengthPrefixesPreventDelimiterCollisions() {
        Map<String, Object> singleEntry = Map.of("a", "b,java.lang.String:c:java.lang.String:d");
        Map<String, Object> twoEntries = linkedMap("a", "b", "c", "d");

        assertNotEquals(BpmFormDataSourceExecutionServiceImpl.digest(singleEntry),
                BpmFormDataSourceExecutionServiceImpl.digest(twoEntries));
        assertNotEquals(BpmFormDataSourceExecutionServiceImpl.digest(Map.of("items", new int[]{1, 2})),
                BpmFormDataSourceExecutionServiceImpl.digest(Map.of("items", List.of(1, 2))));
    }

    @Test
    void execute_nonEmptyResultSchemaDropsUndeclaredFieldsBeforeReturnAndCache() {
        mockPublishedSource("[]", resultSchema(resultField("name", "STRING", null)), false, 60);
        when(provider.execute(any())).thenReturn(new BpmFormDataSourceQueryResult(
                List.of(linkedMap("name", "Alice", "internalSecret", "do-not-leak")), 1, 1));

        BpmFormDataSourceQueryResult result = service.execute(
                "employees", Map.of(), loginUser(1L, 7L), null, null, null);

        assertEquals(Map.of("name", "Alice"), result.getRows().get(0));
        assertTrue(cache.values().stream().noneMatch(value -> value.contains("internalSecret")
                || value.contains("do-not-leak")));
    }

    @Test
    void execute_serviceEnforcesGlobalRowLimitForEveryProvider() {
        properties.setMaxRows(1);
        mockPublishedSource("[]", "[]", false, 0);
        when(provider.execute(any())).thenReturn(new BpmFormDataSourceQueryResult(
                List.of(Map.of("id", 1L), Map.of("id", 2L)), 2, 1));

        ServiceException error = assertThrows(ServiceException.class, () -> service.execute(
                "employees", Map.of(), loginUser(1L, 7L), null, null, null));

        assertEquals(BPM_DATA_SOURCE_ROW_LIMIT.getCode(), error.getCode());
    }

    @Test
    void execute_missingRequiredParameterPreventsProviderInvocation() {
        mockPublishedSource(parameterSchema(param("keyword", "STRING", true)), "[]", false, 0);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.execute("employees", Map.of(), loginUser(1L, 7L), null, null, null));

        assertEquals(BPM_DATA_SOURCE_PARAM_MISSING.getCode(), error.getCode());
        verify(provider, never()).execute(any());
        verify(logMapper).insert(argThat((BpmFormDataSourceLogDO log) -> !log.getSuccess()
                && String.valueOf(BPM_DATA_SOURCE_PARAM_MISSING.getCode()).equals(log.getErrorCode())));
    }

    @Test
    void execute_successWritesAuditAndCapsProviderResult() {
        mockPublishedSource("[]", "[]", false, 0);
        when(provider.execute(any())).thenReturn(new BpmFormDataSourceQueryResult(
                List.of(Map.of("id", 1L), Map.of("id", 2L)), 2, 1));

        BpmFormDataSourceQueryResult result = service.execute(
                "employees", Map.of(), loginUser(1L, 7L), null, 88L, "process-1");

        assertEquals(2, result.getRows().size());
        ArgumentCaptor<BpmFormDataSourceLogDO> logCaptor = ArgumentCaptor.forClass(BpmFormDataSourceLogDO.class);
        verify(logMapper).insert(logCaptor.capture());
        assertTrue(logCaptor.getValue().getSuccess());
        assertEquals(2, logCaptor.getValue().getRowCount());
        assertEquals(88L, logCaptor.getValue().getFormId());
        assertEquals("process-1", logCaptor.getValue().getProcessInstanceId());
    }

    @Test
    void execute_cacheIsTenantAndUserIsolatedButParameterOrderStable() {
        mockPublishedSource("[]", "[]", true, 60);
        when(provider.execute(any())).thenAnswer(invocation -> {
            BpmFormDataSourceExecutionContext context = invocation.getArgument(0);
            return new BpmFormDataSourceQueryResult(
                    List.of(Map.of("tenant", context.getTenantId(), "user", context.getUserId())), 1, 1);
        });

        service.execute("employees", linkedMap("b", 2, "a", 1), loginUser(1L, 7L), null, null, null);
        service.execute("employees", linkedMap("a", 1, "b", 2), loginUser(1L, 7L), null, null, null);
        service.execute("employees", linkedMap("a", 1, "b", 2), loginUser(2L, 7L), null, null, null);
        service.execute("employees", linkedMap("a", 1, "b", 2), loginUser(1L, 8L), null, null, null);

        verify(provider, times(3)).execute(any());
        assertEquals(3, cache.size());
        assertTrue(cache.keySet().stream().noneMatch(key -> key.contains("\"a\"") || key.contains("\"b\"")));
    }

    @Test
    void execute_nonUserScopedCacheIsSharedAcrossUsers() {
        mockPublishedSource("[]", "[]", false, 60);
        when(provider.execute(any())).thenAnswer(invocation -> {
            BpmFormDataSourceExecutionContext context = invocation.getArgument(0);
            return new BpmFormDataSourceQueryResult(List.of(Map.of("loadedBy", context.getUserId())), 1, 1);
        });

        BpmFormDataSourceQueryResult first = service.execute(
                "employees", Map.of(), loginUser(1L, 7L), null, null, null);
        BpmFormDataSourceQueryResult second = service.execute(
                "employees", Map.of(), loginUser(1L, 8L), null, null, null);

        assertEquals(7L, ((Number) first.getRows().get(0).get("loadedBy")).longValue());
        assertEquals(7L, ((Number) second.getRows().get(0).get("loadedBy")).longValue());
        verify(provider, times(1)).execute(any());
    }

    @Test
    void execute_masksBeforeCacheAndCachedValueCannotBeMutatedByCaller() {
        mockPublishedSource("[]", resultSchema(resultField("phone", "STRING", "PHONE")), false, 60);
        Map<String, Object> providerRow = new LinkedHashMap<>();
        providerRow.put("phone", "13812345678");
        when(provider.execute(any())).thenReturn(new BpmFormDataSourceQueryResult(List.of(providerRow), 1, 1));

        BpmFormDataSourceQueryResult first = service.execute(
                "employees", Map.of(), loginUser(1L, 7L), null, null, null);
        assertEquals("138****5678", first.getRows().get(0).get("phone"));
        first.getRows().get(0).put("phone", "poisoned");

        BpmFormDataSourceQueryResult second = service.execute(
                "employees", Map.of(), loginUser(1L, 7L), null, null, null);
        assertEquals("138****5678", second.getRows().get(0).get("phone"));
        verify(provider, times(1)).execute(any());
        assertTrue(cache.values().stream().noneMatch(value -> value.contains("13812345678")));
    }

    @Test
    void execute_failureIsNotCachedAndAuditContainsNoRawValuesOrSql() {
        mockPublishedSource("[]", "[]", false, 60);
        when(provider.execute(any())).thenThrow(new ServiceException(99123, "SECRET_VALUE SELECT * FROM private_table"));

        assertThrows(ServiceException.class, () -> service.execute(
                "employees", Map.of("keyword", "SECRET_VALUE"), loginUser(1L, 7L), null, null, null));
        assertThrows(ServiceException.class, () -> service.execute(
                "employees", Map.of("keyword", "SECRET_VALUE"), loginUser(1L, 7L), null, null, null));

        verify(provider, times(2)).execute(any());
        assertTrue(cache.isEmpty());
        ArgumentCaptor<BpmFormDataSourceLogDO> logs = ArgumentCaptor.forClass(BpmFormDataSourceLogDO.class);
        verify(logMapper, times(2)).insert(logs.capture());
        logs.getAllValues().forEach(log -> {
            assertFalse(log.getSuccess());
            assertEquals("99123", log.getErrorCode());
            assertFalse(log.getParameterDigest().contains("SECRET_VALUE"));
            assertFalse(log.toString().contains("private_table"));
        });
    }

    @Test
    void execute_disabledOrUnpublishedSourceIsRejected() {
        BpmFormDataSourceDO disabled = source().setStatus(1);
        when(dataSourceMapper.selectByCode("employees")).thenReturn(disabled);
        assertEquals(BPM_DATA_SOURCE_UNPUBLISHED.getCode(), assertThrows(ServiceException.class,
                () -> service.execute("employees", Map.of(), loginUser(1L, 7L), null, null, null)).getCode());

        BpmFormDataSourceDO unpublished = source().setPublishedVersion(null);
        when(dataSourceMapper.selectByCode("employees")).thenReturn(unpublished);
        assertEquals(BPM_DATA_SOURCE_UNPUBLISHED.getCode(), assertThrows(ServiceException.class,
                () -> service.execute("employees", Map.of(), loginUser(1L, 7L), null, null, null)).getCode());
        verifyNoInteractions(versionMapper);
        verify(provider, never()).execute(any());
    }

    @Test
    void execute_reservedContextCannotBeOverridden() {
        mockPublishedSource("[]", "[]", false, 0);
        ServiceException error = assertThrows(ServiceException.class, () -> service.execute(
                "employees", Map.of("tenantId", 999L), loginUser(1L, 7L), null, null, null));
        assertEquals(BPM_DATA_SOURCE_PARAM_RESERVED.getCode(), error.getCode());
        verify(provider, never()).execute(any());
        verify(logMapper).insert(argThat((BpmFormDataSourceLogDO log) -> !log.getSuccess()
                && String.valueOf(BPM_DATA_SOURCE_PARAM_RESERVED.getCode()).equals(log.getErrorCode())
                && !log.getParameterDigest().contains("999")));
    }

    @Test
    void sqlExecutor_appliesReadOnlyTimeoutRowLimitAndNamedBindings() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("SELECT id FROM employee WHERE tenant_id = ? AND active = ?"))
                .thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenReturn("id");
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getObject(1)).thenReturn(10L, 11L);

        BpmFormDataSourceExecutor executor = new BpmFormDataSourceExecutor(dataSource);
        List<Map<String, Object>> rows = executor.execute(
                "SELECT id FROM employee WHERE tenant_id = :tenantId AND active = :active",
                linkedMap("active", true, "tenantId", 1L), 2, 3);

        assertEquals(List.of(10L, 11L), rows.stream().map(row -> row.get("id")).toList());
        verify(connection).setReadOnly(true);
        verify(statement).setMaxRows(2);
        verify(statement).setQueryTimeout(3);
        verify(statement).setObject(1, 1L);
        verify(statement).setObject(2, true);
    }

    @Test
    void sqlExecutor_normalizesJdbcDateAndTimestamp() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("SELECT hired_on, created_at FROM employee")).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(2);
        when(metadata.getColumnLabel(1)).thenReturn("hiredOn");
        when(metadata.getColumnLabel(2)).thenReturn("createdAt");
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getObject(1)).thenReturn(java.sql.Date.valueOf("2026-07-18"));
        when(resultSet.getObject(2)).thenReturn(java.sql.Timestamp.valueOf("2026-07-18 10:11:12"));

        List<Map<String, Object>> rows = new BpmFormDataSourceExecutor(dataSource).execute(
                "SELECT hired_on, created_at FROM employee", Map.of(), 2, 3);

        assertEquals(LocalDate.of(2026, 7, 18), rows.get(0).get("hiredOn"));
        assertEquals(LocalDateTime.of(2026, 7, 18, 10, 11, 12), rows.get(0).get("createdAt"));
    }

    @Test
    void dictProvider_mapsActiveDictionaryRows() {
        DictDataApi dictDataApi = mock(DictDataApi.class);
        DictDataRespDTO enabled = dict("Enabled", "1", 0);
        DictDataRespDTO disabled = dict("Disabled", "0", 1);
        when(dictDataApi.getDictDataList("sys_yes_no")).thenReturn(CommonResult.success(List.of(enabled, disabled)));
        BpmDictDataSourceProvider dictProvider = new BpmDictDataSourceProvider(dictDataApi);

        BpmFormDataSourceQueryResult result = dictProvider.execute(context(
                2, "{\"dictType\":\"sys_yes_no\"}", "label", "value", Map.of(), null));

        assertEquals(1, result.getRows().size());
        assertEquals("Enabled", result.getRows().get(0).get("label"));
        assertEquals("1", result.getRows().get(0).get("value"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void platformApiProvider_rejectsUnsafePathAndRedirects() throws Exception {
        BpmFormDataSourceProperties properties = new BpmFormDataSourceProperties();
        properties.getApi().setBaseUrl("http://127.0.0.1:48080");
        properties.getApi().setAllowedPaths(List.of("/admin-api/system/dept/simple-list"));
        HttpClient httpClient = mock(HttpClient.class);
        BpmPlatformApiDataSourceProvider apiProvider = new BpmPlatformApiDataSourceProvider(properties, httpClient);

        List<String> unsafePaths = List.of(
                "https://evil.example/data", "//evil.example/data", "/admin-api/../actuator/env",
                "/admin-api/%2e%2e/actuator/env", "/admin-api/system/dept/simple-list#@evil.example");
        for (String path : unsafePaths) {
            assertEquals(BPM_DATA_SOURCE_CONFIG_INVALID.getCode(), assertThrows(ServiceException.class,
                    () -> apiProvider.execute(context(3, sourceConfig(path, "GET"), null, null,
                            Map.of(), "Bearer sensitive"))).getCode());
        }
        verifyNoInteractions(httpClient);

        HttpResponse<byte[]> redirect = mock(HttpResponse.class);
        when(redirect.statusCode()).thenReturn(302);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(redirect);
        assertEquals(BPM_DATA_SOURCE_EXECUTION_FAILED.getCode(), assertThrows(ServiceException.class,
                () -> apiProvider.execute(context(3,
                        sourceConfig("/admin-api/system/dept/simple-list", "GET"), null, null,
                        Map.of(), "Bearer sensitive"))).getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void platformApiProvider_forwardsAuthenticationAndTenantOnlyToAllowedTarget() throws Exception {
        BpmFormDataSourceProperties properties = new BpmFormDataSourceProperties();
        properties.getApi().setBaseUrl("http://127.0.0.1:48080");
        properties.getApi().setAllowedPaths(List.of("/admin-api/system/dept/simple-list"));
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body("{\"code\":0,\"data\":[{\"id\":3,\"name\":\"Finance\"}]}"));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        BpmPlatformApiDataSourceProvider apiProvider = new BpmPlatformApiDataSourceProvider(properties, httpClient);

        BpmFormDataSourceQueryResult result = apiProvider.execute(context(3,
                sourceConfig("/admin-api/system/dept/simple-list", "GET"), null, null,
                Map.of("keyword", "finance"), "Bearer sensitive"));

        assertEquals("Finance", result.getRows().get(0).get("name"));
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = requestCaptor.getValue();
        assertEquals("127.0.0.1", request.uri().getHost());
        assertEquals("Bearer sensitive", request.headers().firstValue("Authorization").orElseThrow());
        assertEquals("1", request.headers().firstValue("tenant-id").orElseThrow());
    }

    @Test
    @SuppressWarnings("unchecked")
    void platformApiProvider_rejectsResponseLargerThanByteLimit() throws Exception {
        BpmFormDataSourceProperties properties = new BpmFormDataSourceProperties();
        properties.getApi().setBaseUrl("http://127.0.0.1:48080");
        properties.getApi().setAllowedPaths(List.of("/admin-api/system/dept/simple-list"));
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body("{\"id\":1,\"padding\":\"" + "x".repeat(1_048_576) + "\"}"));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        BpmPlatformApiDataSourceProvider apiProvider = new BpmPlatformApiDataSourceProvider(properties, httpClient);
        ServiceException error = assertThrows(ServiceException.class, () -> apiProvider.execute(context(3,
                sourceConfig("/admin-api/system/dept/simple-list", "GET"), null, null, Map.of(), null)));

        assertEquals(BPM_DATA_SOURCE_EXECUTION_FAILED.getCode(), error.getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void platformApiProvider_enforcesRowLimitDuringResponseParsingEvenWhenTotalIsSmall() throws Exception {
        BpmFormDataSourceProperties properties = new BpmFormDataSourceProperties();
        properties.setMaxRows(2);
        properties.getApi().setBaseUrl("http://127.0.0.1:48080");
        properties.getApi().setAllowedPaths(List.of("/admin-api/system/dept/simple-list"));
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body("{\"code\":0,\"data\":{\"list\":[{\"id\":1},{\"id\":2},{\"id\":3}],\"total\":1}}"));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        BpmPlatformApiDataSourceProvider apiProvider = new BpmPlatformApiDataSourceProvider(properties, httpClient);
        ServiceException error = assertThrows(ServiceException.class, () -> apiProvider.execute(context(3,
                sourceConfig("/admin-api/system/dept/simple-list", "GET"), null, null, Map.of(), null)));

        assertEquals(BPM_DATA_SOURCE_ROW_LIMIT.getCode(), error.getCode());
    }

    private void mockPublishedSource(String parameterSchema, String resultSchema, boolean userScoped, int cacheSeconds) {
        mockPublishedSourceWithConfig(parameterSchema, resultSchema,
                "{\"sql\":\"SELECT id FROM employee\",\"userScoped\":" + userScoped + "}", cacheSeconds);
    }

    private void mockPublishedSourceWithConfig(String parameterSchema, String resultSchema,
                                               String sourceConfig, int cacheSeconds) {
        BpmFormDataSourceDO source = source();
        BpmFormDataSourceVersionDO version = version(parameterSchema, resultSchema, sourceConfig, cacheSeconds);
        when(dataSourceMapper.selectByCode("employees")).thenReturn(source);
        when(versionMapper.selectPublished(10L)).thenReturn(version);
    }

    private static BpmFormDataSourceDO source() {
        return new BpmFormDataSourceDO().setId(10L).setName("Employees").setCode("employees")
                .setType(BpmFormDataSourceProvider.TYPE_SQL).setStatus(0).setPublishedVersion(1);
    }

    private static BpmFormDataSourceVersionDO version(String parameterSchema, String resultSchema,
                                                       String sourceConfig, int cacheSeconds) {
        return new BpmFormDataSourceVersionDO().setId(11L).setDataSourceId(10L).setVersion(1).setStatus(0)
                .setSourceConfig(sourceConfig).setParameterSchema(parameterSchema).setResultSchema(resultSchema)
                .setMaxRows(2).setTimeoutSeconds(3).setCacheSeconds(cacheSeconds);
    }

    private static BpmFormDataSourceExecutionContext context(int type, String sourceConfig,
                                                               String labelField, String valueField,
                                                               Map<String, Object> parameters,
                                                               String authorization) {
        BpmFormDataSourceDO source = source().setType(type);
        BpmFormDataSourceVersionDO version = version("[]", "[]", sourceConfig, 0)
                .setLabelField(labelField).setValueField(valueField);
        return new BpmFormDataSourceExecutionContext(source, version, parameters, authorization, 1L, 7L);
    }

    private static LoginUser loginUser(long tenantId, long userId) {
        return new LoginUser().setTenantId(tenantId).setId(userId).setInfo(Map.of("deptId", "3"));
    }

    private static DictDataRespDTO dict(String label, String value, int status) {
        return new DictDataRespDTO().setLabel(label).setValue(value).setDictType("sys_yes_no").setStatus(status);
    }

    private static Map<String, Object> param(String name, String type, boolean required) {
        return linkedMap("name", name, "type", type, "required", required);
    }

    private static Map<String, Object> resultField(String name, String type, String mask) {
        return linkedMap("name", name, "type", type, "mask", mask);
    }

    private static String parameterSchema(Map<String, Object>... parameters) {
        return cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(parameters);
    }

    private static String resultSchema(Map<String, Object>... fields) {
        return cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(fields);
    }

    private static String sourceConfig(String path, String method) {
        return cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(Map.of("path", path, "method", method));
    }

    private static byte[] body(String json) {
        return json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static <K, V> Map<K, V> linkedMap(Object... entries) {
        Map<K, V> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            @SuppressWarnings("unchecked") K key = (K) entries[i];
            @SuppressWarnings("unchecked") V value = (V) entries[i + 1];
            result.put(key, value);
        }
        return result;
    }

}
