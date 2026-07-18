package cn.iocoder.yudao.module.bpm.service.definition;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.datasource.BpmFormDataSourceSaveReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.datasource.BpmFormDataSourceVersionSaveReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDataSourceDO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDataSourceVersionDO;
import cn.iocoder.yudao.module.bpm.dal.mysql.definition.BpmFormDataSourceMapper;
import cn.iocoder.yudao.module.bpm.dal.mysql.definition.BpmFormDataSourceVersionMapper;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmFormDataSourceVersionStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.datasource.BpmFormDataSourceProperties;
import cn.iocoder.yudao.module.bpm.framework.datasource.BpmFormDataSourceProvider;
import cn.iocoder.yudao.module.bpm.framework.datasource.BpmFormDataSourceQueryResult;
import cn.iocoder.yudao.module.bpm.framework.datasource.BpmFormDataSourceSqlValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BpmFormDataSourceServiceTest {

    @Mock
    private BpmFormDataSourceMapper dataSourceMapper;
    @Mock
    private BpmFormDataSourceVersionMapper versionMapper;
    @Mock
    private BpmFormDataSourceExecutionService executionService;

    private BpmFormDataSourceService service;

    @BeforeEach
    void setUp() {
        BpmFormDataSourceProperties properties = new BpmFormDataSourceProperties();
        properties.getApi().setAllowedPaths(List.of("/admin-api/system/dept/simple-list"));
        service = new BpmFormDataSourceServiceImpl(dataSourceMapper, versionMapper, executionService,
                new BpmFormDataSourceSqlValidator(), properties);
        lenient().when(dataSourceMapper.updateById(any(BpmFormDataSourceDO.class))).thenReturn(1);
        lenient().when(versionMapper.updateDraft(any(BpmFormDataSourceVersionDO.class))).thenReturn(1);
    }

    @Test
    void createDataSource_createsEnabledDefinitionWithoutImplicitDraft() {
        doAnswer(invocation -> {
            invocation.<BpmFormDataSourceDO>getArgument(0).setId(10L);
            return 1;
        }).when(dataSourceMapper).insert(any(BpmFormDataSourceDO.class));

        Long id = service.createDataSource(sourceReq("oa_available_seals"));

        assertEquals(10L, id);
        verify(dataSourceMapper).insert(org.mockito.ArgumentMatchers.<BpmFormDataSourceDO>argThat(
                source -> CommonStatusEnum.isEnable(source.getStatus())
                && source.getPublishedVersion() == null));
        verifyNoInteractions(versionMapper);
    }

    @Test
    void createDataSourceWithDraft_createsDefinitionAndInitialDraftAsOneOperation() {
        doAnswer(invocation -> {
            invocation.<BpmFormDataSourceDO>getArgument(0).setId(10L);
            return 1;
        }).when(dataSourceMapper).insert(any(BpmFormDataSourceDO.class));
        when(dataSourceMapper.selectByIdForUpdate(10L)).thenReturn(source(10L, null));
        when(versionMapper.selectLatestDraft(10L)).thenReturn(null);
        when(versionMapper.selectNextVersion(10L)).thenReturn(1);
        doAnswer(invocation -> {
            invocation.<BpmFormDataSourceVersionDO>getArgument(0).setId(20L);
            return 1;
        }).when(versionMapper).insert(any(BpmFormDataSourceVersionDO.class));

        Long sourceId = service.createDataSourceWithDraft(sourceReq("oa_available_seals"),
                sqlVersionReq("SELECT id, name FROM oa_seal WHERE tenant_id = :tenantId"));

        assertEquals(10L, sourceId);
        InOrder inOrder = inOrder(dataSourceMapper, versionMapper);
        inOrder.verify(dataSourceMapper).insert(any(BpmFormDataSourceDO.class));
        inOrder.verify(versionMapper).insert(org.mockito.ArgumentMatchers.<BpmFormDataSourceVersionDO>argThat(
                version -> version.getDataSourceId() == 10L && version.getVersion() == 1
                        && BpmFormDataSourceVersionStatusEnum.isDraft(version.getStatus())));
    }

    @Test
    void createDataSourceWithDraft_validatesDraftBeforeCreatingDefinition() {
        ServiceException error = assertThrows(ServiceException.class, () -> service.createDataSourceWithDraft(
                sourceReq("oa_available_seals"), sqlVersionReq("DELETE FROM oa_seal")));

        assertEquals(BPM_DATA_SOURCE_SQL_READ_ONLY.getCode(), error.getCode());
        verify(dataSourceMapper, never()).insert(any(BpmFormDataSourceDO.class));
        verifyNoInteractions(versionMapper);
    }

    @Test
    void createDataSource_duplicateCodeHasStableBusinessError() {
        when(dataSourceMapper.selectByCode("oa_available_seals")).thenReturn(new BpmFormDataSourceDO().setId(1L));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createDataSource(sourceReq("oa_available_seals")));

        assertEquals(BPM_DATA_SOURCE_CODE_DUPLICATE.getCode(), error.getCode());
        verify(dataSourceMapper, never()).insert(any(BpmFormDataSourceDO.class));
    }

    @Test
    void createDataSource_duplicateKeyRaceHasStableBusinessError() {
        doThrow(new DuplicateKeyException("unique index including sensitive database details"))
                .when(dataSourceMapper).insert(any(BpmFormDataSourceDO.class));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createDataSource(sourceReq("oa_available_seals")));

        assertEquals(BPM_DATA_SOURCE_CODE_DUPLICATE.getCode(), error.getCode());
    }

    @Test
    void createDataSource_invalidNullTypeUsesBusinessErrorInsteadOfNullPointer() {
        BpmFormDataSourceSaveReqVO req = sourceReq("oa_available_seals").setType(null);

        ServiceException error = assertThrows(ServiceException.class, () -> service.createDataSource(req));

        assertEquals(BPM_DATA_SOURCE_CONFIG_INVALID.getCode(), error.getCode());
    }

    @Test
    void getDataSource_missingUsesStableBusinessError() {
        ServiceException error = assertThrows(ServiceException.class, () -> service.getDataSource(404L));

        assertEquals(BPM_DATA_SOURCE_NOT_EXISTS.getCode(), error.getCode());
    }

    @Test
    void updateDataSource_onlyNameIsMutable() {
        BpmFormDataSourceDO current = source(10L, null);
        when(dataSourceMapper.selectByIdForUpdate(10L)).thenReturn(current);
        BpmFormDataSourceSaveReqVO rename = sourceReq("oa_available_seals").setId(10L).setName("公司可用印章");

        service.updateDataSource(rename);

        verify(dataSourceMapper).updateById(org.mockito.ArgumentMatchers.<BpmFormDataSourceDO>argThat(
                update -> update.getId() == 10L
                && "公司可用印章".equals(update.getName())
                && "oa_available_seals".equals(update.getCode())
                && update.getType() == BpmFormDataSourceProvider.TYPE_SQL));

        BpmFormDataSourceSaveReqVO codeChange = sourceReq("oa_other_seals").setId(10L);
        ServiceException error = assertThrows(ServiceException.class, () -> service.updateDataSource(codeChange));
        assertEquals(BPM_DATA_SOURCE_VERSION_STATE_INVALID.getCode(), error.getCode());
    }

    @Test
    void saveDraft_firstVersionIsOneAndLatestDraftIsEditedInPlace() {
        BpmFormDataSourceDO source = source(10L, null);
        BpmFormDataSourceVersionDO draft = version(20L, 1,
                BpmFormDataSourceVersionStatusEnum.DRAFT.getStatus());
        when(dataSourceMapper.selectByIdForUpdate(10L)).thenReturn(source);
        when(versionMapper.selectLatestDraft(10L)).thenReturn(null, draft);
        when(versionMapper.selectNextVersion(10L)).thenReturn(1);
        doAnswer(invocation -> {
            invocation.<BpmFormDataSourceVersionDO>getArgument(0).setId(20L);
            return 1;
        }).when(versionMapper).insert(any(BpmFormDataSourceVersionDO.class));

        Long firstId = service.saveDraft(10L, sqlVersionReq("SELECT id, name FROM oa_seal WHERE tenant_id = :tenantId"));
        Long secondId = service.saveDraft(10L, sqlVersionReq("SELECT id, name FROM oa_seal WHERE tenant_id = :tenantId"));

        assertEquals(20L, firstId);
        assertEquals(20L, secondId);
        verify(versionMapper).insert(org.mockito.ArgumentMatchers.<BpmFormDataSourceVersionDO>argThat(
                item -> item.getVersion() == 1
                && BpmFormDataSourceVersionStatusEnum.isDraft(item.getStatus())));
        verify(versionMapper).updateDraft(org.mockito.ArgumentMatchers.<BpmFormDataSourceVersionDO>argThat(
                item -> item.getId() == 20L
                && BpmFormDataSourceVersionStatusEnum.isDraft(item.getStatus())));
    }

    @Test
    void saveDraft_afterPublishCreatesNextImmutableVersion() {
        BpmFormDataSourceDO source = source(10L, 1);
        when(dataSourceMapper.selectByIdForUpdate(10L)).thenReturn(source);
        when(versionMapper.selectLatestDraft(10L)).thenReturn(null);
        when(versionMapper.selectNextVersion(10L)).thenReturn(2);
        doAnswer(invocation -> {
            invocation.<BpmFormDataSourceVersionDO>getArgument(0).setId(21L);
            return 1;
        }).when(versionMapper).insert(any(BpmFormDataSourceVersionDO.class));

        Long versionId = service.saveDraft(10L,
                sqlVersionReq("SELECT id, name FROM oa_seal WHERE tenant_id = :tenantId"));

        assertEquals(21L, versionId);
        verify(versionMapper).insert(org.mockito.ArgumentMatchers.<BpmFormDataSourceVersionDO>argThat(
                item -> item.getVersion() == 2
                && BpmFormDataSourceVersionStatusEnum.isDraft(item.getStatus())));
    }

    @Test
    void saveDraft_invalidSqlNeverPersists() {
        when(dataSourceMapper.selectByIdForUpdate(10L)).thenReturn(source(10L, null));
        BpmFormDataSourceVersionSaveReqVO req = sqlVersionReq("DELETE FROM oa_seal");

        ServiceException error = assertThrows(ServiceException.class, () -> service.saveDraft(10L, req));

        assertEquals(BPM_DATA_SOURCE_SQL_READ_ONLY.getCode(), error.getCode());
        verify(versionMapper, never()).insert(any(BpmFormDataSourceVersionDO.class));
        verify(versionMapper, never()).updateDraft(any(BpmFormDataSourceVersionDO.class));
    }

    @Test
    void saveDraft_rejectsMissingSchemaLabel() {
        when(dataSourceMapper.selectByIdForUpdate(10L)).thenReturn(source(10L, 1));
        BpmFormDataSourceVersionSaveReqVO req = sqlVersionReq(
                "SELECT id, name FROM oa_seal WHERE tenant_id = :tenantId")
                .setResultSchema("[{\"name\":\"id\",\"type\":\"LONG\"}]")
                .setLabelField("id").setValueField("id");

        ServiceException error = assertThrows(ServiceException.class, () -> service.saveDraft(10L, req));

        assertEquals(BPM_DATA_SOURCE_CONFIG_INVALID.getCode(), error.getCode());
        verifyNoInteractions(versionMapper);
    }

    @Test
    void saveDraft_rejectsBlankSchemaLabel() {
        when(dataSourceMapper.selectByIdForUpdate(10L)).thenReturn(source(10L, 1));
        BpmFormDataSourceVersionSaveReqVO req = sqlVersionReq(
                "SELECT id, name FROM oa_seal WHERE tenant_id = :tenantId")
                .setResultSchema("[{\"name\":\"id\",\"label\":\"   \",\"type\":\"LONG\"}]")
                .setLabelField("id").setValueField("id");

        ServiceException error = assertThrows(ServiceException.class, () -> service.saveDraft(10L, req));

        assertEquals(BPM_DATA_SOURCE_CONFIG_INVALID.getCode(), error.getCode());
        verifyNoInteractions(versionMapper);
    }

    @Test
    void saveDraft_rejectsOverlongSchemaLabel() {
        when(dataSourceMapper.selectByIdForUpdate(10L)).thenReturn(source(10L, 1));
        BpmFormDataSourceVersionSaveReqVO req = sqlVersionReq(
                "SELECT id, name FROM oa_seal WHERE tenant_id = :tenantId")
                .setResultSchema("[{\"name\":\"id\",\"label\":\"" + "中".repeat(65)
                        + "\",\"type\":\"LONG\"}]")
                .setLabelField("id").setValueField("id");

        ServiceException error = assertThrows(ServiceException.class, () -> service.saveDraft(10L, req));

        assertEquals(BPM_DATA_SOURCE_CONFIG_INVALID.getCode(), error.getCode());
        verifyNoInteractions(versionMapper);
    }

    @Test
    void saveDraft_rejectsMixedTypedOrUnknownConfiguration() {
        when(dataSourceMapper.selectByIdForUpdate(10L)).thenReturn(source(10L, null));
        BpmFormDataSourceVersionSaveReqVO req = sqlVersionReq(
                "SELECT id, name FROM oa_seal WHERE tenant_id = :tenantId")
                .setSourceConfig("{\"sql\":\"SELECT id, name FROM oa_seal WHERE tenant_id = :tenantId\"," +
                        "\"dictType\":\"sys_yes_no\"}");

        ServiceException error = assertThrows(ServiceException.class, () -> service.saveDraft(10L, req));

        assertEquals(BPM_DATA_SOURCE_CONFIG_INVALID.getCode(), error.getCode());
        verify(versionMapper, never()).insert(any(BpmFormDataSourceVersionDO.class));
    }

    @Test
    void saveDraft_sqlRequiresServerTenantParameter() {
        when(dataSourceMapper.selectByIdForUpdate(10L)).thenReturn(source(10L, null));
        BpmFormDataSourceVersionSaveReqVO req = sqlVersionReq("SELECT id, name FROM oa_seal")
                .setParameterSchema("[]");

        ServiceException error = assertThrows(ServiceException.class, () -> service.saveDraft(10L, req));

        assertEquals(BPM_DATA_SOURCE_CONFIG_INVALID.getCode(), error.getCode());
        verify(versionMapper, never()).insert(any(BpmFormDataSourceVersionDO.class));
    }

    @Test
    void saveDraft_rejectsSchemaNamesThatRuntimeCannotAddress() {
        when(dataSourceMapper.selectByIdForUpdate(10L)).thenReturn(source(10L, null));
        BpmFormDataSourceVersionSaveReqVO unsafeParameter = sqlVersionReq(
                "SELECT id, name FROM oa_seal WHERE tenant_id = :tenantId")
                .setParameterSchema("""
                        [{"name":"tenantId","label":"当前租户","type":"LONG","required":true},
                         {"name":"customer-id","label":"客户编号","type":"LONG","required":false}]
                        """);
        ServiceException parameterError = assertThrows(ServiceException.class,
                () -> service.saveDraft(10L, unsafeParameter));
        assertEquals(BPM_DATA_SOURCE_CONFIG_INVALID.getCode(), parameterError.getCode());

        BpmFormDataSourceVersionSaveReqVO unsafeResult = sqlVersionReq(
                "SELECT id, name FROM oa_seal WHERE tenant_id = :tenantId")
                .setResultSchema("[{\"name\":\"keeper.name\",\"label\":\"保管人\",\"type\":\"STRING\"}]")
                .setLabelField("keeper.name").setValueField("keeper.name");
        ServiceException resultError = assertThrows(ServiceException.class,
                () -> service.saveDraft(10L, unsafeResult));
        assertEquals(BPM_DATA_SOURCE_CONFIG_INVALID.getCode(), resultError.getCode());

        BpmFormDataSourceVersionSaveReqVO prototypeResult = sqlVersionReq(
                "SELECT id, name FROM oa_seal WHERE tenant_id = :tenantId")
                .setResultSchema("[{\"name\":\"constructor\",\"label\":\"构造器\",\"type\":\"STRING\"}]")
                .setLabelField("constructor").setValueField("constructor");
        ServiceException prototypeError = assertThrows(ServiceException.class,
                () -> service.saveDraft(10L, prototypeResult));
        assertEquals(BPM_DATA_SOURCE_CONFIG_INVALID.getCode(), prototypeError.getCode());

        String overlongName = "a".repeat(64);
        BpmFormDataSourceVersionSaveReqVO overlongResult = sqlVersionReq(
                "SELECT id, name FROM oa_seal WHERE tenant_id = :tenantId")
                .setResultSchema("[{\"name\":\"" + overlongName
                        + "\",\"label\":\"超长字段\",\"type\":\"STRING\"}]")
                .setLabelField(overlongName).setValueField(overlongName);
        ServiceException overlongError = assertThrows(ServiceException.class,
                () -> service.saveDraft(10L, overlongResult));
        assertEquals(BPM_DATA_SOURCE_CONFIG_INVALID.getCode(), overlongError.getCode());

        verify(versionMapper, never()).insert(any(BpmFormDataSourceVersionDO.class));
        verify(versionMapper, never()).updateDraft(any(BpmFormDataSourceVersionDO.class));
    }

    @Test
    void saveDraft_validatesDictionaryAndPlatformApiTypedConfiguration() {
        BpmFormDataSourceDO dictionary = source(10L, null).setType(BpmFormDataSourceProvider.TYPE_DICT);
        BpmFormDataSourceDO platformApi = source(11L, null).setType(BpmFormDataSourceProvider.TYPE_PLATFORM_API);
        when(dataSourceMapper.selectByIdForUpdate(10L)).thenReturn(dictionary);
        when(dataSourceMapper.selectByIdForUpdate(11L)).thenReturn(platformApi);
        when(versionMapper.selectNextVersion(10L)).thenReturn(1);
        when(versionMapper.selectNextVersion(11L)).thenReturn(1);
        doAnswer(invocation -> {
            BpmFormDataSourceVersionDO version = invocation.getArgument(0);
            version.setId(version.getDataSourceId() + 100L);
            return 1;
        }).when(versionMapper).insert(any(BpmFormDataSourceVersionDO.class));

        Long dictionaryVersion = service.saveDraft(10L,
                baseVersionReq("{\"dictType\":\"sys_yes_no\"}", "[]"));
        Long apiVersion = service.saveDraft(11L,
                baseVersionReq("{\"path\":\"/admin-api/system/dept/simple-list\",\"method\":\"GET\"}",
                        "[{\"name\":\"id\",\"label\":\"编号\",\"type\":\"LONG\"}]"));

        assertEquals(110L, dictionaryVersion);
        assertEquals(111L, apiVersion);
        ServiceException unsafePath = assertThrows(ServiceException.class, () -> service.saveDraft(11L,
                baseVersionReq("{\"path\":\"http://evil.example/data\",\"method\":\"GET\"}",
                        "[{\"name\":\"id\",\"label\":\"编号\",\"type\":\"LONG\"}]")));
        assertEquals(BPM_DATA_SOURCE_CONFIG_INVALID.getCode(), unsafePath.getCode());
        ServiceException writeMethod = assertThrows(ServiceException.class, () -> service.saveDraft(11L,
                baseVersionReq("{\"path\":\"/admin-api/system/dept/simple-list\",\"method\":\"POST\"}",
                        "[{\"name\":\"id\",\"label\":\"编号\",\"type\":\"LONG\"}]")));
        assertEquals(BPM_DATA_SOURCE_CONFIG_INVALID.getCode(), writeMethod.getCode());
    }

    @Test
    void publish_movesDraftAndPublishedPointerAtomically() {
        BpmFormDataSourceDO source = source(10L, null);
        BpmFormDataSourceVersionDO draft = version(20L, 1,
                BpmFormDataSourceVersionStatusEnum.DRAFT.getStatus());
        when(dataSourceMapper.selectByIdForUpdate(10L)).thenReturn(source);
        when(versionMapper.selectByIdAndSourceId(20L, 10L)).thenReturn(draft);
        when(versionMapper.publishDraft(20L, 10L)).thenReturn(1);

        service.publish(10L, 20L);

        verify(versionMapper).publishDraft(20L, 10L);
        verify(dataSourceMapper).updateById(org.mockito.ArgumentMatchers.<BpmFormDataSourceDO>argThat(
                item -> item.getId() == 10L
                && item.getPublishedVersion() == 1
                && CommonStatusEnum.isEnable(item.getStatus())));
    }

    @Test
    void publish_rejectsPublishedVersionAndConditionalConflict() {
        when(dataSourceMapper.selectByIdForUpdate(10L)).thenReturn(source(10L, 1));
        when(versionMapper.selectByIdAndSourceId(20L, 10L)).thenReturn(version(20L, 1,
                BpmFormDataSourceVersionStatusEnum.PUBLISHED.getStatus()));

        ServiceException immutable = assertThrows(ServiceException.class, () -> service.publish(10L, 20L));
        assertEquals(BPM_DATA_SOURCE_VERSION_STATE_INVALID.getCode(), immutable.getCode());

        reset(versionMapper);
        when(versionMapper.selectByIdAndSourceId(21L, 10L)).thenReturn(version(21L, 2,
                BpmFormDataSourceVersionStatusEnum.DRAFT.getStatus()));
        when(versionMapper.publishDraft(21L, 10L)).thenReturn(0);
        ServiceException conflict = assertThrows(ServiceException.class, () -> service.publish(10L, 21L));
        assertEquals(BPM_DATA_SOURCE_VERSION_CONFLICT.getCode(), conflict.getCode());
    }

    @Test
    void trialRun_executesPersistedDraftWithoutCacheButKeepsAuditContext() {
        BpmFormDataSourceDO source = source(10L, 1);
        BpmFormDataSourceVersionDO draft = version(21L, 2,
                BpmFormDataSourceVersionStatusEnum.DRAFT.getStatus());
        when(dataSourceMapper.selectById(10L)).thenReturn(source);
        when(versionMapper.selectByIdAndSourceId(21L, 10L)).thenReturn(draft);
        LoginUser user = new LoginUser().setId(7L).setTenantId(1L);
        BpmFormDataSourceQueryResult expected = new BpmFormDataSourceQueryResult(List.of(Map.of("id", 1L)), 1, 2);
        when(executionService.executeVersion(source, draft, Map.of("keyword", "seal"), user,
                "Bearer token", null, null, false)).thenReturn(expected);

        BpmFormDataSourceQueryResult result = service.trialRun(
                10L, 21L, Map.of("keyword", "seal"), user, "Bearer token");

        assertSame(expected, result);
        verify(executionService).executeVersion(source, draft, Map.of("keyword", "seal"), user,
                "Bearer token", null, null, false);
    }

    @Test
    void trialRun_rejectsPublishedVersion() {
        when(dataSourceMapper.selectById(10L)).thenReturn(source(10L, 1));
        when(versionMapper.selectByIdAndSourceId(20L, 10L)).thenReturn(version(20L, 1,
                BpmFormDataSourceVersionStatusEnum.PUBLISHED.getStatus()));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.trialRun(10L, 20L, Map.of(), new LoginUser().setId(7L), null));

        assertEquals(BPM_DATA_SOURCE_VERSION_STATE_INVALID.getCode(), error.getCode());
        verifyNoInteractions(executionService);
    }

    @Test
    void disable_locksAndDisablesDefinitionWithoutChangingHistory() {
        when(dataSourceMapper.selectByIdForUpdate(10L)).thenReturn(source(10L, 1));

        service.disable(10L);

        verify(dataSourceMapper).updateById(org.mockito.ArgumentMatchers.<BpmFormDataSourceDO>argThat(
                item -> item.getId() == 10L
                && CommonStatusEnum.isDisable(item.getStatus()) && item.getPublishedVersion() == 1));
        verifyNoInteractions(versionMapper);
    }

    private static BpmFormDataSourceSaveReqVO sourceReq(String code) {
        return new BpmFormDataSourceSaveReqVO().setName("可用印章").setCode(code)
                .setType(BpmFormDataSourceProvider.TYPE_SQL);
    }

    private static BpmFormDataSourceDO source(Long id, Integer publishedVersion) {
        return new BpmFormDataSourceDO().setId(id).setName("可用印章").setCode("oa_available_seals")
                .setType(BpmFormDataSourceProvider.TYPE_SQL).setStatus(CommonStatusEnum.ENABLE.getStatus())
                .setPublishedVersion(publishedVersion);
    }

    private static BpmFormDataSourceVersionDO version(Long id, int version, int status) {
        return new BpmFormDataSourceVersionDO().setId(id).setDataSourceId(10L).setVersion(version).setStatus(status)
                .setSourceConfig("{\"sql\":\"SELECT id, name FROM oa_seal WHERE tenant_id = :tenantId\"}")
                .setParameterSchema("[{\"name\":\"tenantId\",\"label\":\"当前租户\","
                        + "\"type\":\"LONG\",\"required\":true}]")
                .setResultSchema("[{\"name\":\"id\",\"label\":\"编号\",\"type\":\"LONG\"},"
                        + "{\"name\":\"name\",\"label\":\"名称\",\"type\":\"STRING\"}]")
                .setLabelField("name").setValueField("id").setPageable(false)
                .setMaxRows(100).setTimeoutSeconds(3).setCacheSeconds(30);
    }

    private static BpmFormDataSourceVersionSaveReqVO sqlVersionReq(String sql) {
        return new BpmFormDataSourceVersionSaveReqVO()
                .setSourceConfig("{\"sql\":\"" + sql.replace("\"", "\\\"") + "\"}")
                .setParameterSchema("[{\"name\":\"tenantId\",\"label\":\"当前租户\","
                        + "\"type\":\"LONG\",\"required\":true}]")
                .setResultSchema("[{\"name\":\"id\",\"label\":\"编号\",\"type\":\"LONG\"},"
                        + "{\"name\":\"name\",\"label\":\"名称\",\"type\":\"STRING\"}]")
                .setLabelField("name").setValueField("id").setPageable(false)
                .setMaxRows(100).setTimeoutSeconds(3).setCacheSeconds(30);
    }

    private static BpmFormDataSourceVersionSaveReqVO baseVersionReq(String sourceConfig, String resultSchema) {
        return new BpmFormDataSourceVersionSaveReqVO().setSourceConfig(sourceConfig)
                .setParameterSchema("[]").setResultSchema(resultSchema).setPageable(false)
                .setMaxRows(100).setTimeoutSeconds(3).setCacheSeconds(0);
    }

}
