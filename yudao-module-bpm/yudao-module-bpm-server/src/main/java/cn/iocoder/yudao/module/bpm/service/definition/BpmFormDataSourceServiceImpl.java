package cn.iocoder.yudao.module.bpm.service.definition;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.datasource.BpmFormDataSourcePageReqVO;
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
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.Data;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.*;

/** Default lifecycle implementation. Published version rows are never updated. */
@Service
@Validated
public class BpmFormDataSourceServiceImpl implements BpmFormDataSourceService {

    private static final Pattern SOURCE_CODE = Pattern.compile("^[a-z][a-z0-9_]{0,126}$");
    private static final Pattern SAFE_API_PATH = Pattern.compile("^/[A-Za-z0-9_/-]+$");
    private static final Set<Integer> SOURCE_TYPES = Set.of(
            BpmFormDataSourceProvider.TYPE_SQL,
            BpmFormDataSourceProvider.TYPE_DICT,
            BpmFormDataSourceProvider.TYPE_PLATFORM_API);
    private static final Set<String> SCHEMA_TYPES = Set.of(
            "STRING", "LONG", "INTEGER", "DECIMAL", "NUMBER", "BOOLEAN", "DATE", "DATETIME");
    private static final Set<String> MASK_STRATEGIES = Set.of("FULL", "PHONE", "EMAIL", "ID_CARD", "BANK_CARD");

    private final BpmFormDataSourceMapper dataSourceMapper;
    private final BpmFormDataSourceVersionMapper versionMapper;
    private final BpmFormDataSourceExecutionService executionService;
    private final BpmFormDataSourceSqlValidator sqlValidator;
    private final BpmFormDataSourceProperties properties;

    public BpmFormDataSourceServiceImpl(BpmFormDataSourceMapper dataSourceMapper,
                                        BpmFormDataSourceVersionMapper versionMapper,
                                        BpmFormDataSourceExecutionService executionService,
                                        BpmFormDataSourceSqlValidator sqlValidator,
                                        BpmFormDataSourceProperties properties) {
        this.dataSourceMapper = dataSourceMapper;
        this.versionMapper = versionMapper;
        this.executionService = executionService;
        this.sqlValidator = sqlValidator;
        this.properties = properties;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDataSource(BpmFormDataSourceSaveReqVO reqVO) {
        validateDefinition(reqVO);
        validateCodeUnique(null, reqVO.getCode());
        BpmFormDataSourceDO source = new BpmFormDataSourceDO()
                .setName(reqVO.getName().trim()).setCode(reqVO.getCode().trim()).setType(reqVO.getType())
                .setStatus(CommonStatusEnum.ENABLE.getStatus()).setPublishedVersion(null);
        try {
            dataSourceMapper.insert(source);
        } catch (DuplicateKeyException ex) {
            throw exception(BPM_DATA_SOURCE_CODE_DUPLICATE, reqVO.getCode());
        }
        return source.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDataSource(BpmFormDataSourceSaveReqVO reqVO) {
        if (reqVO == null || reqVO.getId() == null) {
            throw exception(BPM_DATA_SOURCE_NOT_EXISTS);
        }
        validateDefinition(reqVO);
        BpmFormDataSourceDO current = requireSourceForUpdate(reqVO.getId());
        // Codes are persisted in form definitions and type determines the typed version configuration. Both are
        // immutable after creation; changing either would silently break existing form bindings or reinterpret drafts.
        if (!Objects.equals(current.getCode(), reqVO.getCode())
                || !Objects.equals(current.getType(), reqVO.getType())) {
            throw exception(BPM_DATA_SOURCE_VERSION_STATE_INVALID);
        }
        validateCodeUnique(current.getId(), reqVO.getCode());
        BpmFormDataSourceDO update = new BpmFormDataSourceDO().setId(current.getId())
                .setName(reqVO.getName().trim()).setCode(reqVO.getCode().trim()).setType(reqVO.getType());
        try {
            if (dataSourceMapper.updateById(update) != 1) {
                throw exception(BPM_DATA_SOURCE_VERSION_CONFLICT);
            }
        } catch (DuplicateKeyException ex) {
            throw exception(BPM_DATA_SOURCE_CODE_DUPLICATE, reqVO.getCode());
        }
    }

    @Override
    public BpmFormDataSourceDO getDataSource(Long id) {
        return requireSource(id);
    }

    @Override
    public PageResult<BpmFormDataSourceDO> getDataSourcePage(BpmFormDataSourcePageReqVO reqVO) {
        return dataSourceMapper.selectPage(reqVO);
    }

    @Override
    public List<BpmFormDataSourceDO> getSimpleDataSourceList() {
        return dataSourceMapper.selectEnabledPublishedList();
    }

    @Override
    public List<BpmFormDataSourceVersionDO> getVersionList(Long sourceId) {
        requireSource(sourceId);
        return versionMapper.selectListBySourceId(sourceId);
    }

    @Override
    public BpmFormDataSourceVersionDO getVersion(Long sourceId, Long versionId) {
        requireSource(sourceId);
        return requireVersion(sourceId, versionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveDraft(Long sourceId, BpmFormDataSourceVersionSaveReqVO reqVO) {
        BpmFormDataSourceDO source = requireSourceForUpdate(sourceId);
        validateVersion(source.getType(), reqVO);
        BpmFormDataSourceVersionDO draft = versionMapper.selectLatestDraft(sourceId);
        if (draft != null) {
            if (!BpmFormDataSourceVersionStatusEnum.isDraft(draft.getStatus())) {
                throw exception(BPM_DATA_SOURCE_VERSION_STATE_INVALID);
            }
            int updated = versionMapper.updateDraft(copyVersion(reqVO, new BpmFormDataSourceVersionDO()
                    .setId(draft.getId()).setDataSourceId(sourceId).setVersion(draft.getVersion())
                    .setStatus(BpmFormDataSourceVersionStatusEnum.DRAFT.getStatus())));
            if (updated != 1) {
                throw exception(BPM_DATA_SOURCE_VERSION_CONFLICT);
            }
            return draft.getId();
        }

        Integer nextVersion = versionMapper.selectNextVersion(sourceId);
        BpmFormDataSourceVersionDO created = copyVersion(reqVO, new BpmFormDataSourceVersionDO()
                .setDataSourceId(sourceId).setVersion(nextVersion)
                .setStatus(BpmFormDataSourceVersionStatusEnum.DRAFT.getStatus()));
        try {
            versionMapper.insert(created);
        } catch (DuplicateKeyException ex) {
            throw exception(BPM_DATA_SOURCE_VERSION_CONFLICT);
        }
        return created.getId();
    }

    @Override
    public BpmFormDataSourceQueryResult trialRun(Long sourceId, Long versionId,
                                                 Map<String, Object> parameters, LoginUser loginUser,
                                                 String authorization) {
        BpmFormDataSourceDO source = requireSource(sourceId);
        BpmFormDataSourceVersionDO version = requireVersion(sourceId, versionId);
        if (!BpmFormDataSourceVersionStatusEnum.isDraft(version.getStatus())) {
            throw exception(BPM_DATA_SOURCE_VERSION_STATE_INVALID);
        }
        validateVersion(source.getType(), toSaveReq(version));
        return executionService.executeVersion(source, version, parameters, loginUser, authorization,
                null, null, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long sourceId, Long versionId) {
        BpmFormDataSourceDO source = requireSourceForUpdate(sourceId);
        BpmFormDataSourceVersionDO version = requireVersion(sourceId, versionId);
        if (!BpmFormDataSourceVersionStatusEnum.isDraft(version.getStatus())) {
            throw exception(BPM_DATA_SOURCE_VERSION_STATE_INVALID);
        }
        validateVersion(source.getType(), toSaveReq(version));
        if (versionMapper.publishDraft(versionId, sourceId) != 1) {
            throw exception(BPM_DATA_SOURCE_VERSION_CONFLICT);
        }
        if (dataSourceMapper.updateById(new BpmFormDataSourceDO().setId(sourceId)
                .setPublishedVersion(version.getVersion()).setStatus(source.getStatus())) != 1) {
            throw exception(BPM_DATA_SOURCE_VERSION_CONFLICT);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disable(Long id) {
        BpmFormDataSourceDO source = requireSourceForUpdate(id);
        if (CommonStatusEnum.isDisable(source.getStatus())) {
            return;
        }
        if (dataSourceMapper.updateById(new BpmFormDataSourceDO().setId(id)
                .setStatus(CommonStatusEnum.DISABLE.getStatus())
                .setPublishedVersion(source.getPublishedVersion())) != 1) {
            throw exception(BPM_DATA_SOURCE_VERSION_CONFLICT);
        }
    }

    private BpmFormDataSourceDO requireSource(Long id) {
        BpmFormDataSourceDO source = id == null ? null : dataSourceMapper.selectById(id);
        if (source == null) {
            throw exception(BPM_DATA_SOURCE_NOT_EXISTS);
        }
        return source;
    }

    private BpmFormDataSourceDO requireSourceForUpdate(Long id) {
        BpmFormDataSourceDO source = id == null ? null : dataSourceMapper.selectByIdForUpdate(id);
        if (source == null) {
            throw exception(BPM_DATA_SOURCE_NOT_EXISTS);
        }
        return source;
    }

    private BpmFormDataSourceVersionDO requireVersion(Long sourceId, Long versionId) {
        BpmFormDataSourceVersionDO version = versionId == null ? null
                : versionMapper.selectByIdAndSourceId(versionId, sourceId);
        if (version == null) {
            throw exception(BPM_DATA_SOURCE_VERSION_NOT_EXISTS);
        }
        return version;
    }

    private void validateCodeUnique(Long id, String code) {
        BpmFormDataSourceDO existing = dataSourceMapper.selectByCode(code);
        if (existing != null && !Objects.equals(existing.getId(), id)) {
            throw exception(BPM_DATA_SOURCE_CODE_DUPLICATE, code);
        }
    }

    private static void validateDefinition(BpmFormDataSourceSaveReqVO reqVO) {
        if (reqVO == null || !StringUtils.hasText(reqVO.getName()) || reqVO.getName().trim().length() > 63
                || !StringUtils.hasText(reqVO.getCode()) || !SOURCE_CODE.matcher(reqVO.getCode()).matches()
                || reqVO.getType() == null || !SOURCE_TYPES.contains(reqVO.getType())) {
            throw exception(BPM_DATA_SOURCE_CONFIG_INVALID);
        }
    }

    private void validateVersion(Integer sourceType, BpmFormDataSourceVersionSaveReqVO reqVO) {
        if (reqVO == null || sourceType == null || !SOURCE_TYPES.contains(sourceType)
                || !StringUtils.hasText(reqVO.getSourceConfig())
                || reqVO.getPageable() == null || !positiveWithin(reqVO.getMaxRows(), properties.getMaxRows())
                || !positiveWithin(reqVO.getTimeoutSeconds(), properties.getTimeoutSeconds())
                || reqVO.getCacheSeconds() != null && reqVO.getCacheSeconds() < 0) {
            throw exception(BPM_DATA_SOURCE_CONFIG_INVALID);
        }
        List<SchemaField> parameterFields = parseSchema(reqVO.getParameterSchema());
        List<SchemaField> resultFields = parseSchema(reqVO.getResultSchema());
        validateFieldReference(reqVO.getLabelField(), resultFields);
        validateFieldReference(reqVO.getValueField(), resultFields);

        Map<String, Object> config = JsonUtils.parseObjectQuietly(reqVO.getSourceConfig(), new TypeReference<>() {});
        if (config == null) {
            throw exception(BPM_DATA_SOURCE_CONFIG_INVALID);
        }
        switch (sourceType) {
            case BpmFormDataSourceProvider.TYPE_SQL -> validateSql(config, parameterFields, resultFields);
            case BpmFormDataSourceProvider.TYPE_DICT -> validateDict(config);
            case BpmFormDataSourceProvider.TYPE_PLATFORM_API -> validatePlatformApi(config, resultFields);
            default -> throw exception(BPM_DATA_SOURCE_CONFIG_INVALID);
        }
    }

    private void validateSql(Map<String, Object> config, List<SchemaField> parameterFields,
                             List<SchemaField> resultFields) {
        if (!Set.of("sql", "userScoped").containsAll(config.keySet())
                || config.containsKey("userScoped") && !(config.get("userScoped") instanceof Boolean)
                || !(config.get("sql") instanceof String sql) || !StringUtils.hasText(sql) || resultFields.isEmpty()) {
            throw exception(BPM_DATA_SOURCE_CONFIG_INVALID);
        }
        Set<String> referencedParameters = sqlValidator.validateAndExtractParameters(sql);
        Set<String> declaredParameters = parameterFields.stream().map(SchemaField::getName).collect(Collectors.toSet());
        // This is the minimum isolation floor for administrator-authored SQL. The tenant value is supplied only by
        // the server context resolver; cache scoping alone cannot prevent a query from reading another tenant's rows.
        if (!referencedParameters.contains("tenantId") || !declaredParameters.containsAll(referencedParameters)) {
            throw exception(BPM_DATA_SOURCE_CONFIG_INVALID);
        }
    }

    private static void validateDict(Map<String, Object> config) {
        if (!Set.of("dictType").containsAll(config.keySet())
                || !(config.get("dictType") instanceof String dictType) || !StringUtils.hasText(dictType)) {
            throw exception(BPM_DATA_SOURCE_CONFIG_INVALID);
        }
    }

    private void validatePlatformApi(Map<String, Object> config, List<SchemaField> resultFields) {
        String path = config.get("path") instanceof String value ? value : null;
        String method = config.get("method") instanceof String value ? value.toUpperCase(Locale.ROOT) : "GET";
        if (!Set.of("path", "method").containsAll(config.keySet())
                || config.containsKey("method") && !(config.get("method") instanceof String)
                || !StringUtils.hasText(path) || !SAFE_API_PATH.matcher(path).matches() || path.contains("..")
                || path.contains("//") || path.contains("\\") || !"GET".equals(method)
                || properties.getApi().getAllowedPaths() == null
                || properties.getApi().getAllowedPaths().stream().noneMatch(path::equals)
                || resultFields.isEmpty()) {
            throw exception(BPM_DATA_SOURCE_CONFIG_INVALID);
        }
    }

    private static List<SchemaField> parseSchema(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<SchemaField> fields = JsonUtils.parseArray(json, SchemaField.class);
            Set<String> names = new HashSet<>();
            for (SchemaField field : fields) {
                String type = StringUtils.hasText(field.getType()) ? field.getType().toUpperCase(Locale.ROOT) : null;
                String mask = StringUtils.hasText(field.getMask()) ? field.getMask() : field.getMaskStrategy();
                if (!BpmFormDataSourceSchemaRules.isSafeFieldName(field.getName()) || !names.add(field.getName())
                        || type == null || !SCHEMA_TYPES.contains(type)
                        || StringUtils.hasText(mask) && !MASK_STRATEGIES.contains(mask.toUpperCase(Locale.ROOT))) {
                    throw exception(BPM_DATA_SOURCE_CONFIG_INVALID);
                }
            }
            return fields;
        } catch (ServiceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw exception(BPM_DATA_SOURCE_CONFIG_INVALID);
        }
    }

    private static void validateFieldReference(String fieldName, List<SchemaField> resultFields) {
        if (StringUtils.hasText(fieldName)
                && resultFields.stream().noneMatch(field -> fieldName.equals(field.getName()))) {
            throw exception(BPM_DATA_SOURCE_CONFIG_INVALID);
        }
    }

    private static boolean positiveWithin(Integer value, int globalLimit) {
        return value == null || value > 0 && value <= Math.max(1, globalLimit);
    }

    private static BpmFormDataSourceVersionDO copyVersion(BpmFormDataSourceVersionSaveReqVO reqVO,
                                                           BpmFormDataSourceVersionDO target) {
        return target.setSourceConfig(reqVO.getSourceConfig()).setParameterSchema(reqVO.getParameterSchema())
                .setResultSchema(reqVO.getResultSchema()).setLabelField(reqVO.getLabelField())
                .setValueField(reqVO.getValueField()).setPageable(reqVO.getPageable())
                .setMaxRows(reqVO.getMaxRows()).setTimeoutSeconds(reqVO.getTimeoutSeconds())
                .setCacheSeconds(reqVO.getCacheSeconds());
    }

    private static BpmFormDataSourceVersionSaveReqVO toSaveReq(BpmFormDataSourceVersionDO version) {
        return new BpmFormDataSourceVersionSaveReqVO().setSourceConfig(version.getSourceConfig())
                .setParameterSchema(version.getParameterSchema()).setResultSchema(version.getResultSchema())
                .setLabelField(version.getLabelField()).setValueField(version.getValueField())
                .setPageable(version.getPageable()).setMaxRows(version.getMaxRows())
                .setTimeoutSeconds(version.getTimeoutSeconds()).setCacheSeconds(version.getCacheSeconds());
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
