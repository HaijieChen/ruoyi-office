package cn.iocoder.yudao.module.bpm.service.definition;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDataSourceDO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDataSourceVersionDO;
import cn.iocoder.yudao.module.bpm.dal.mysql.definition.BpmFormDataSourceMapper;
import cn.iocoder.yudao.module.bpm.dal.mysql.definition.BpmFormDataSourceVersionMapper;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmFormDataSourceVersionStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BpmFormLinkageValidatorTest {

    @Mock
    private BpmFormDataSourceMapper dataSourceMapper;
    @Mock
    private BpmFormDataSourceVersionMapper versionMapper;

    private BpmFormLinkageValidator validator;
    private BpmFormDataSourceVersionDO publishedVersion;

    @BeforeEach
    void setUp() {
        validator = new BpmFormLinkageValidator(dataSourceMapper, versionMapper);
        BpmFormDataSourceDO source = new BpmFormDataSourceDO().setId(10L).setCode("oa_available_seals")
                .setStatus(CommonStatusEnum.ENABLE.getStatus()).setPublishedVersion(2);
        publishedVersion = new BpmFormDataSourceVersionDO().setId(20L).setDataSourceId(10L)
                .setVersion(2).setStatus(BpmFormDataSourceVersionStatusEnum.PUBLISHED.getStatus())
                .setParameterSchema("""
                        [{"name":"tenantId","type":"LONG","required":true},
                         {"name":"keyword","type":"STRING","required":true}]
                        """)
                .setResultSchema("""
                        [{"name":"id","type":"LONG"},{"name":"name","type":"STRING"},
                         {"name":"keeperName","type":"STRING"}]
                        """).setLabelField("name").setValueField("id").setPageable(false);
        lenient().when(dataSourceMapper.selectByCode("oa_available_seals")).thenReturn(source);
        lenient().when(versionMapper.selectPublished(10L)).thenReturn(publishedVersion);
    }

    @Test
    void validate_acceptsPublishedSourceBindingsMappingsAndNestedFields() {
        List<String> fields = List.of(
                field("keyword"),
                """
                {"type":"group","children":[
                  {"type":"input","field":"keeperName"},
                  {"type":"RemoteDataSourceSelect","field":"sealIds","props":{
                    "dataSourceCode":"oa_available_seals",
                    "parameterBindings":{"keyword":"FORM.keyword"},
                    "dependencies":["keyword"],
                    "outputMappings":{"keeperName":"keeperName"},
                    "labelField":"name","valueField":"id"
                  }}
                ]}
                """);

        assertDoesNotThrow(() -> validator.validate(fields));
    }

    @Test
    void validate_requiresExistingEnabledPublishedSource() {
        when(dataSourceMapper.selectByCode("missing_source")).thenReturn(null);

        assertCode(BPM_DATA_SOURCE_UNPUBLISHED, () -> validator.validate(List.of(remote("missing_source", "sealIds",
                "{}"))));

        when(dataSourceMapper.selectByCode("disabled_source")).thenReturn(new BpmFormDataSourceDO().setId(11L)
                .setCode("disabled_source").setStatus(CommonStatusEnum.DISABLE.getStatus()).setPublishedVersion(1));
        assertCode(BPM_DATA_SOURCE_UNPUBLISHED, () -> validator.validate(List.of(remote("disabled_source", "sealIds",
                "{}"))));
    }

    @Test
    void validate_requiresBindingsOnlyForNonReservedRequiredParameters() {
        assertCode(BPM_DATA_SOURCE_PARAM_MISSING,
                () -> validator.validate(List.of(remote("oa_available_seals", "sealIds", "{}"))));

        assertCode(BPM_DATA_SOURCE_PARAM_RESERVED, () -> validator.validate(List.of(
                field("keyword"),
                remote("oa_available_seals", "sealIds",
                        "\"parameterBindings\":{\"keyword\":\"FORM.keyword\",\"tenantId\":\"USER.id\"},"
                                + "\"dependencies\":[\"keyword\"]"))));
    }

    @Test
    void validate_acceptsBusinessParameterBoundToCompanyFieldButRejectsReservedCompanyIdBinding() {
        publishedVersion.setParameterSchema("""
                [{"name":"tenantId","type":"LONG","required":true},
                 {"name":"selectedCompanyId","type":"LONG","required":true}]
                """);

        assertDoesNotThrow(() -> validator.validate(List.of(
                field("companyId"),
                remote("oa_available_seals", "sealIds",
                        "\"parameterBindings\":{\"selectedCompanyId\":\"FORM.companyId\"},"
                                + "\"dependencies\":[\"companyId\"]"))));

        publishedVersion.setParameterSchema("[{\"name\":\"companyId\",\"type\":\"LONG\",\"required\":true}]");
        assertCode(BPM_DATA_SOURCE_PARAM_RESERVED, () -> validator.validate(List.of(
                field("companyId"),
                remote("oa_available_seals", "sealIds",
                        "\"parameterBindings\":{\"companyId\":\"FORM.companyId\"},"
                                + "\"dependencies\":[\"companyId\"]"))));
    }

    @Test
    void validate_appliesTheFrontendBindingExpressionWhitelist() {
        assertDoesNotThrow(() -> validator.validate(List.of(
                remote("oa_available_seals", "sealIds",
                        "\"parameterBindings\":{\"keyword\":\"USER.companyId\"}"))));
        assertDoesNotThrow(() -> validator.validate(List.of(
                remote("oa_available_seals", "sealIds",
                        "\"parameterBindings\":{\"keyword\":\"PROCESS.instanceId\"}"))));

        for (String unsafe : List.of("alert(1)", "FORM.keyword || USER.id", "FORM[keyword]",
                "FORM.__proto__.value", "USER.password", "USER.id.value", "PROCESS.businessKey",
                "OTHER.keyword")) {
            assertCode(BPM_DATA_SOURCE_CONFIG_INVALID, () -> validator.validate(List.of(
                    field("keyword"),
                    remote("oa_available_seals", "sealIds",
                            "\"parameterBindings\":{\"keyword\":\"" + unsafe + "\"},"
                                    + "\"dependencies\":[\"keyword\"]"))));
        }
    }

    @Test
    void validate_rejectsUnknownBindingAndMalformedRemoteProperties() {
        assertCode(BPM_DATA_SOURCE_CONFIG_INVALID, () -> validator.validate(List.of(
                field("keyword"),
                remote("oa_available_seals", "sealIds",
                        "\"parameterBindings\":{\"keyword\":\"FORM.keyword\",\"unknown\":\"FORM.keyword\"},"
                                + "\"dependencies\":[\"keyword\"]"))));

        assertCode(BPM_DATA_SOURCE_CONFIG_INVALID, () -> validator.validate(List.of(
                "{\"type\":\"RemoteDataSourceSelect\",\"field\":\"sealIds\",\"props\":{}}")));
    }

    @Test
    void validate_checksResultMappingSourceAndTargetFields() {
        assertCode(BPM_DATA_SOURCE_RESULT_MAPPING_MISMATCH, () -> validator.validate(List.of(
                field("keyword"), field("keeperName"),
                remote("oa_available_seals", "sealIds",
                        "\"parameterBindings\":{\"keyword\":\"FORM.keyword\"},"
                                + "\"dependencies\":[\"keyword\"],"
                                + "\"outputMappings\":{\"unknownResult\":\"keeperName\"}"))));

        assertCode(BPM_DATA_SOURCE_RESULT_MAPPING_MISMATCH, () -> validator.validate(List.of(
                field("keyword"),
                remote("oa_available_seals", "sealIds",
                        "\"parameterBindings\":{\"keyword\":\"FORM.keyword\"},"
                                + "\"dependencies\":[\"keyword\"],"
                                + "\"outputMappings\":{\"keeperName\":\"missingTarget\"}"))));

        assertCode(BPM_DATA_SOURCE_RESULT_MAPPING_MISMATCH, () -> validator.validate(List.of(
                field("keyword"),
                remote("oa_available_seals", "sealIds",
                        "\"parameterBindings\":{\"keyword\":\"FORM.keyword\"},"
                                + "\"dependencies\":[\"keyword\"],\"labelField\":\"missingLabel\""))));
    }

    @Test
    void validate_checksEffectiveVersionLabelAndValueFields() {
        publishedVersion.setLabelField("missingLabel");

        assertCode(BPM_DATA_SOURCE_RESULT_MAPPING_MISMATCH, () -> validator.validate(List.of(
                field("keyword"),
                remote("oa_available_seals", "sealIds",
                        "\"parameterBindings\":{\"keyword\":\"FORM.keyword\"},"
                                + "\"dependencies\":[\"keyword\"]"))));
    }

    @Test
    void validate_rejectsSchemaNamesThatRuntimeOrFrontendCannotAddress() {
        publishedVersion.setParameterSchema("""
                [{"name":"tenantId","type":"LONG","required":true},
                 {"name":"customer-id","type":"STRING","required":true}]
                """);
        assertCode(BPM_DATA_SOURCE_CONFIG_INVALID, () -> validator.validate(List.of(
                field("keyword"),
                remote("oa_available_seals", "sealIds",
                        "\"parameterBindings\":{\"customer-id\":\"FORM.keyword\"},"
                                + "\"dependencies\":[\"keyword\"]"))));

        publishedVersion.setParameterSchema("[]").setResultSchema(
                "[{\"name\":\"keeper.name\",\"type\":\"STRING\"}]")
                .setLabelField("keeper.name").setValueField("keeper.name");
        assertCode(BPM_DATA_SOURCE_CONFIG_INVALID, () -> validator.validate(List.of(
                remote("oa_available_seals", "sealIds", "{}"))));

        publishedVersion.setResultSchema("[{\"name\":\"constructor\",\"type\":\"STRING\"}]")
                .setLabelField("constructor").setValueField("constructor");
        assertCode(BPM_DATA_SOURCE_CONFIG_INVALID, () -> validator.validate(List.of(
                remote("oa_available_seals", "sealIds", "{}"))));
    }

    @Test
    void validate_requiresEffectiveLabelAndValueFields() {
        publishedVersion.setLabelField(null);

        assertCode(BPM_DATA_SOURCE_RESULT_MAPPING_MISMATCH, () -> validator.validate(List.of(
                field("keyword"),
                remote("oa_available_seals", "sealIds",
                        "\"parameterBindings\":{\"keyword\":\"FORM.keyword\"},"
                                + "\"dependencies\":[\"keyword\"]"))));
    }

    @Test
    void validate_requiresEveryFormBindingRootInDependencies() {
        assertCode(BPM_DATA_SOURCE_CONFIG_INVALID, () -> validator.validate(List.of(
                field("keyword"),
                remote("oa_available_seals", "sealIds",
                        "\"parameterBindings\":{\"keyword\":\"FORM.keyword\"}"))));
    }

    @Test
    void validate_rejectsExecutableOrEndpointOverrideProperties() {
        assertCode(BPM_DATA_SOURCE_CONFIG_INVALID, () -> validator.validate(List.of(
                field("keyword"),
                remote("oa_available_seals", "sealIds",
                        "\"parameterBindings\":{\"keyword\":\"FORM.keyword\"},"
                                + "\"dependencies\":[\"keyword\"],\"url\":\"https://evil.example\""))));
    }

    @Test
    void validate_rejectsInvalidLinkageStrategyDuplicateDependenciesAndPageabilityMismatch() {
        assertCode(BPM_DATA_SOURCE_CONFIG_INVALID, () -> validator.validate(List.of(
                field("keyword"),
                remote("oa_available_seals", "sealIds",
                        "\"parameterBindings\":{\"keyword\":\"FORM.keyword\"},"
                                + "\"dependencies\":[\"keyword\"],\"onDependencyChange\":\"eval-and-load\""))));

        assertCode(BPM_DATA_SOURCE_CONFIG_INVALID, () -> validator.validate(List.of(
                field("keyword"),
                remote("oa_available_seals", "sealIds",
                        "\"parameterBindings\":{\"keyword\":\"FORM.keyword\"},"
                                + "\"dependencies\":[\"keyword\",\"keyword\"]"))));

        assertCode(BPM_DATA_SOURCE_CONFIG_INVALID, () -> validator.validate(List.of(
                field("keyword"),
                remote("oa_available_seals", "sealIds",
                        "\"parameterBindings\":{\"keyword\":\"FORM.keyword\"},"
                                + "\"dependencies\":[\"keyword\"],\"pageable\":true"))));

        assertCode(BPM_DATA_SOURCE_CONFIG_INVALID, () -> validator.validate(List.of(
                field("keyword"),
                remote("oa_available_seals", "sealIds",
                        "\"parameterBindings\":{\"keyword\":\"FORM.keyword\"},"
                                + "\"dependencies\":[\"keyword\"],\"pageable\":null"))));
    }

    @Test
    void validate_requiresSearchParameterToBeSafePublishedAndNotBoundTwice() {
        publishedVersion.setParameterSchema("""
                [{"name":"tenantId","type":"LONG","required":true},
                 {"name":"keyword","type":"STRING","required":true}]
                """);

        assertDoesNotThrow(() -> validator.validate(List.of(
                remote("oa_available_seals", "sealIds", "\"searchParamName\":\"keyword\""))));

        for (String invalidName : List.of("missing", "tenantId", "__proto__")) {
            assertCode(BPM_DATA_SOURCE_CONFIG_INVALID, () -> validator.validate(List.of(
                    remote("oa_available_seals", "sealIds",
                            "\"searchParamName\":\"" + invalidName + "\""))));
        }

        assertCode(BPM_DATA_SOURCE_CONFIG_INVALID, () -> validator.validate(List.of(
                remote("oa_available_seals", "sealIds",
                        "\"searchParamName\":\"keyword\"," +
                                "\"parameterBindings\":{\"keyword\":\"USER.companyId\"}"))));
    }

    @Test
    void validate_requiresCompletePublishedPaginationControls() {
        publishedVersion.setPageable(true).setParameterSchema("""
                [{"name":"tenantId","type":"LONG","required":true},
                 {"name":"pageNo","type":"INTEGER","required":true},
                 {"name":"pageSize","type":"INTEGER","required":true}]
                """);

        assertDoesNotThrow(() -> validator.validate(List.of(
                remote("oa_available_seals", "sealIds",
                        "\"pageable\":true,\"pageNoParamName\":\"pageNo\"," +
                                "\"pageSizeParamName\":\"pageSize\",\"pageSize\":20"))));

        for (String invalidControls : List.of(
                "\"pageable\":true",
                "\"pageable\":true,\"pageNoParamName\":\"pageNo\"",
                "\"pageable\":true,\"pageNoParamName\":\"missing\",\"pageSizeParamName\":\"pageSize\"",
                "\"pageable\":true,\"pageNoParamName\":\"tenantId\",\"pageSizeParamName\":\"pageSize\"",
                "\"pageable\":true,\"pageNoParamName\":\"pageNo\",\"pageSizeParamName\":\"pageNo\"",
                "\"pageable\":true,\"pageNoParamName\":\"pageNo\",\"pageSizeParamName\":\"pageSize\",\"pageSize\":201")) {
            assertCode(BPM_DATA_SOURCE_CONFIG_INVALID, () -> validator.validate(List.of(
                    remote("oa_available_seals", "sealIds", invalidControls))));
        }
    }

    @Test
    void validate_rejectsPaginationControlsWhenComponentPaginationIsDisabled() {
        publishedVersion.setParameterSchema("""
                [{"name":"tenantId","type":"LONG","required":true},
                 {"name":"pageNo","type":"INTEGER"},
                 {"name":"pageSize","type":"INTEGER"}]
                """);

        assertCode(BPM_DATA_SOURCE_CONFIG_INVALID, () -> validator.validate(List.of(
                remote("oa_available_seals", "sealIds",
                        "\"pageable\":false,\"pageNoParamName\":\"pageNo\"," +
                                "\"pageSizeParamName\":\"pageSize\""))));
    }

    @Test
    void validate_requiresSnapshotTargetToBeDistinctExistingOrdinaryField() {
        publishedVersion.setParameterSchema(
                "[{\"name\":\"tenantId\",\"type\":\"LONG\",\"required\":true}]");

        assertDoesNotThrow(() -> validator.validate(List.of(
                field("sealSnapshot"),
                remote("oa_available_seals", "sealIds", "\"snapshotField\":\"sealSnapshot\""))));

        for (String invalidTarget : List.of("missingSnapshot", "sealIds")) {
            assertCode(BPM_DATA_SOURCE_CONFIG_INVALID, () -> validator.validate(List.of(
                    field("sealSnapshot"),
                    remote("oa_available_seals", "sealIds",
                            "\"snapshotField\":\"" + invalidTarget + "\""))));
        }

        assertCode(BPM_DATA_SOURCE_CONFIG_INVALID, () -> validator.validate(List.of(
                remote("oa_available_seals", "sealSnapshot", "{}"),
                remote("oa_available_seals", "sealIds", "\"snapshotField\":\"sealSnapshot\""))));
    }

    @Test
    void validate_rejectsDuplicateOutputTargetsWithinAndAcrossSelectors() {
        assertCode(BPM_DATA_SOURCE_RESULT_MAPPING_MISMATCH, () -> validator.validate(List.of(
                field("keyword"), field("keeperName"),
                remote("oa_available_seals", "sealIds",
                        "\"parameterBindings\":{\"keyword\":\"FORM.keyword\"},"
                                + "\"dependencies\":[\"keyword\"],"
                                + "\"outputMappings\":{\"name\":\"keeperName\",\"keeperName\":\"keeperName\"}"))));

        assertCode(BPM_DATA_SOURCE_RESULT_MAPPING_MISMATCH, () -> validator.validate(List.of(
                field("keyword"), field("keeperName"),
                remote("oa_available_seals", "sealIds",
                        "\"parameterBindings\":{\"keyword\":\"FORM.keyword\"},"
                                + "\"dependencies\":[\"keyword\"],\"outputMappings\":{\"name\":\"keeperName\"}"),
                remote("oa_available_seals", "vehicleIds",
                        "\"parameterBindings\":{\"keyword\":\"FORM.keyword\"},"
                                + "\"dependencies\":[\"keyword\"],\"outputMappings\":{\"name\":\"keeperName\"}"))));
    }

    @Test
    void validate_requiresDependenciesToExistAndRejectsCycles() {
        assertCode(BPM_DATA_SOURCE_CONFIG_INVALID, () -> validator.validate(List.of(
                field("keyword"),
                remote("oa_available_seals", "sealIds",
                        "\"parameterBindings\":{\"keyword\":\"FORM.keyword\"},"
                                + "\"dependencies\":[\"missingField\"]"))));

        List<String> cyclicFields = List.of(
                remote("oa_available_seals", "companyId",
                        "\"parameterBindings\":{\"keyword\":\"FORM.sealIds\"},\"dependencies\":[\"sealIds\"]"),
                remote("oa_available_seals", "sealIds",
                        "\"parameterBindings\":{\"keyword\":\"FORM.companyId\"},\"dependencies\":[\"companyId\"]"));
        assertCode(BPM_DATA_SOURCE_DEPENDENCY_CYCLE, () -> validator.validate(cyclicFields));

        assertCode(BPM_DATA_SOURCE_DEPENDENCY_CYCLE, () -> validator.validate(List.of(
                field("companyId"),
                remote("oa_available_seals", "sealIds",
                        "\"parameterBindings\":{\"keyword\":\"FORM.companyId\"},"
                                + "\"dependencies\":[\"companyId\"],"
                                + "\"outputMappings\":{\"name\":\"companyId\"}"))));
    }

    @Test
    void validate_rejectsDuplicateNewAndLegacyFieldsButKeepsLegacyRemoteAliasCompatible() {
        assertCode(FORM_FIELD_REPEAT, () -> validator.validate(List.of(
                "{\"label\":\"旧字段\",\"vModel\":\"companyId\"}",
                "{\"type\":\"input\",\"title\":\"新字段\",\"field\":\"companyId\"}")));

        assertDoesNotThrow(() -> validator.validate(List.of(
                "{\"type\":\"RemoteSelect\",\"field\":\"legacySealIds\","
                        + "\"props\":{\"dataSourceCode\":\"missing_source\"}}")));
    }

    @Test
    void validate_keepsFormCreateTextualChildrenCompatible() {
        assertDoesNotThrow(() -> validator.validate(List.of("""
                {"type":"div","children":["说明文字",
                  {"type":"input","field":"memo","children":["输入提示"]}]}
                """)));
    }

    @Test
    void validate_rejectsRemoteSelectorsInsideScopedSubformsAndTableForms() {
        assertCode(BPM_DATA_SOURCE_CONFIG_INVALID, () -> validator.validate(List.of("""
                {"type":"subForm","field":"items","props":{"rule":[
                  {"type":"RemoteDataSourceSelect","field":"sealIds","props":{
                    "dataSourceCode":"oa_available_seals",
                    "parameterBindings":{"keyword":"USER.companyId"}
                  }}
                ]}}
                """)));

        assertCode(BPM_DATA_SOURCE_CONFIG_INVALID, () -> validator.validate(List.of("""
                {"type":"tableForm","field":"rows","props":{"columns":[
                  {"label":"印章","rule":[
                    {"type":"RemoteDataSourceSelect","field":"sealIds","props":{
                      "dataSourceCode":"oa_available_seals",
                      "parameterBindings":{"keyword":"USER.companyId"}
                    }}
                  ]}
                ]}}
                """)));
    }

    @Test
    void validate_keepsScopedLegacyFieldsIndependentButValidatesControlRules() {
        assertDoesNotThrow(() -> validator.validate(List.of("""
                {"type":"subForm","field":"itemsA","props":{"rule":[
                  {"type":"input","field":"name"}
                ]}}
                """, """
                {"type":"subForm","field":"itemsB","props":{"rule":[
                  {"type":"input","field":"name"}
                ]}}
                """)));

        assertDoesNotThrow(() -> validator.validate(List.of("""
                {"type":"select","field":"mode","control":[
                  {"value":"seal","rule":[
                    {"type":"RemoteDataSourceSelect","field":"sealIds","props":{
                      "dataSourceCode":"oa_available_seals",
                      "parameterBindings":{"keyword":"USER.companyId"}
                    }}
                  ]}
                ]}
                """)));
    }

    private static String field(String field) {
        return "{\"type\":\"input\",\"field\":\"" + field + "\"}";
    }

    private static String remote(String sourceCode, String field, String additionalProps) {
        String suffix = "{}".equals(additionalProps) ? "" : "," + additionalProps;
        return "{\"type\":\"RemoteDataSourceSelect\",\"field\":\"" + field + "\",\"props\":{"
                + "\"dataSourceCode\":\"" + sourceCode + "\"" + suffix + "}}";
    }

    private static void assertCode(cn.iocoder.yudao.framework.common.exception.ErrorCode expected,
                                   org.junit.jupiter.api.function.Executable executable) {
        ServiceException error = assertThrows(ServiceException.class, executable);
        assertEquals(expected.getCode(), error.getCode());
    }

}
