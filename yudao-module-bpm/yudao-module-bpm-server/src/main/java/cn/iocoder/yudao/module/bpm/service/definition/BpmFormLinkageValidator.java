package cn.iocoder.yudao.module.bpm.service.definition;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDataSourceDO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDataSourceVersionDO;
import cn.iocoder.yudao.module.bpm.dal.mysql.definition.BpmFormDataSourceMapper;
import cn.iocoder.yudao.module.bpm.dal.mysql.definition.BpmFormDataSourceVersionMapper;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmFormDataSourceVersionStatusEnum;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.BPM_DATA_SOURCE_CONFIG_INVALID;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.BPM_DATA_SOURCE_DEPENDENCY_CYCLE;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.BPM_DATA_SOURCE_PARAM_MISSING;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.BPM_DATA_SOURCE_PARAM_RESERVED;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.BPM_DATA_SOURCE_RESULT_MAPPING_MISMATCH;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.BPM_DATA_SOURCE_UNPUBLISHED;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.FORM_FIELD_REPEAT;

/**
 * Validates dynamic-form data-source linkages before a form definition is persisted.
 *
 * <p>Legacy form fields are still accepted. Strict data-source validation is activated only by the exact
 * {@code RemoteDataSourceSelect} component emitted by the current form-create designer.</p>
 */
@Component
@RequiredArgsConstructor
public class BpmFormLinkageValidator {

    private static final Set<String> RESERVED_PARAMETERS = Set.of("tenantId", "userId", "deptId", "companyId");
    private static final Set<String> DANGEROUS_REMOTE_PROPERTIES = Set.of(
            "url", "sql", "method", "parseFunc", "parser", "transform", "request");
    private static final Set<String> DEPENDENCY_CHANGE_STRATEGIES = Set.of(
            "clear-and-reload", "keep-and-revalidate");
    private static final Set<String> FORBIDDEN_BINDING_SEGMENTS = Set.of(
            "__proto__", "constructor", "prototype");
    private static final Set<String> USER_BINDING_FIELDS = Set.of("id", "deptId", "companyId");
    private static final Set<String> PROCESS_BINDING_FIELDS = Set.of("definitionKey", "instanceId");
    private static final Pattern BINDING_PATH = Pattern.compile(
            "^(FORM|PROCESS|USER)(?:\\.[A-Za-z_][A-Za-z0-9_]*)+$");

    private final BpmFormDataSourceMapper dataSourceMapper;
    private final BpmFormDataSourceVersionMapper versionMapper;

    public void validate(List<String> fields) {
        BpmFormDataSourceReferenceValidator.ReferenceParseResult references =
                BpmFormDataSourceReferenceValidator.parseReferences(fields);
        if (!references.valid()) {
            throw exception(BPM_DATA_SOURCE_CONFIG_INVALID);
        }

        ParseContext context = new ParseContext();
        if (fields != null) {
            for (String fieldJson : fields) {
                JsonNode root = BpmFormDataSourceReferenceValidator.readObject(fieldJson);
                if (root == null) {
                    throw exception(BPM_DATA_SOURCE_CONFIG_INVALID);
                }
                BpmFormDataSourceReferenceValidator.walkRules(root,
                        (node, scoped) -> collect(node, scoped, context));
            }
        }

        // Reusing the runtime parser is intentional: a component accepted at save time must be the same exact
        // component that can authorize a runtime data-source request later.
        if (references.references().size() != context.linkageNodes.size()) {
            throw exception(BPM_DATA_SOURCE_CONFIG_INVALID);
        }

        Map<String, PublishedSource> publishedSources = new HashMap<>();
        for (LinkageNode node : context.linkageNodes) {
            PublishedSource published = publishedSources.computeIfAbsent(node.dataSourceCode,
                    this::requirePublishedSource);
            validateBindings(node, parseSchema(published.version.getParameterSchema()), context.fields.keySet());
            validateResults(node, published.version, parseSchema(published.version.getResultSchema()),
                    context.fields.keySet());
            validateDependencies(node, context.fields.keySet());
        }
        validateUniqueOutputWriters(context.linkageNodes);
        validateAcyclic(context.linkageNodes);
    }

    private static void collect(JsonNode node, boolean scoped, ParseContext context) {
        if (scoped) {
            // Sub-form and table-form fields have their own row/object namespace. Until linkage expressions become
            // scope-aware, reject remote selectors there explicitly instead of silently saving an unusable form.
            if (BpmFormDataSourceReferenceValidator.REMOTE_COMPONENT_TYPE.equals(text(node.get("type")))) {
                throw exception(BPM_DATA_SOURCE_CONFIG_INVALID);
            }
            return;
        }

        String field = text(node.get("field"));
        if (!StringUtils.hasText(field)) {
            field = text(node.get("vModel")); // legacy form-generator field name
        }
        if (StringUtils.hasText(field)) {
            String label = firstText(node.get("title"), node.get("label"), field);
            String oldLabel = context.fields.putIfAbsent(field, label);
            if (oldLabel != null) {
                throw exception(FORM_FIELD_REPEAT, oldLabel, label, field);
            }
        }

        if (BpmFormDataSourceReferenceValidator.REMOTE_COMPONENT_TYPE.equals(text(node.get("type")))) {
            context.linkageNodes.add(parseLinkageNode(node, field));
        }
    }

    private static LinkageNode parseLinkageNode(JsonNode node, String field) {
        JsonNode props = node.get("props");
        String sourceCode = props != null && props.isObject() ? text(props.get("dataSourceCode")) : null;
        if (!StringUtils.hasText(field) || !StringUtils.hasText(sourceCode)) {
            throw exception(BPM_DATA_SOURCE_CONFIG_INVALID);
        }
        if (DANGEROUS_REMOTE_PROPERTIES.stream().anyMatch(props::has)) {
            throw exception(BPM_DATA_SOURCE_CONFIG_INVALID);
        }
        Map<String, String> parameterBindings = readStringMap(props, "parameterBindings");
        List<String> dependencies = readStringList(props, "dependencies");
        Map<String, String> outputMappings = readStringMap(props, "outputMappings");
        String labelField = optionalText(props, "labelField");
        String valueField = optionalText(props, "valueField");
        String dependencyChange = optionalText(props, "onDependencyChange");
        if (dependencyChange != null && !DEPENDENCY_CHANGE_STRATEGIES.contains(dependencyChange)) {
            throw exception(BPM_DATA_SOURCE_CONFIG_INVALID);
        }
        Boolean pageable = optionalBoolean(props, "pageable");
        return new LinkageNode(field, sourceCode, parameterBindings, dependencies, outputMappings,
                labelField, valueField, pageable);
    }

    private PublishedSource requirePublishedSource(String code) {
        BpmFormDataSourceDO source = dataSourceMapper.selectByCode(code);
        if (source == null || !CommonStatusEnum.isEnable(source.getStatus()) || source.getPublishedVersion() == null) {
            throw exception(BPM_DATA_SOURCE_UNPUBLISHED);
        }
        BpmFormDataSourceVersionDO version = versionMapper.selectPublished(source.getId());
        if (version == null || !BpmFormDataSourceVersionStatusEnum.isPublished(version.getStatus())
                || !source.getPublishedVersion().equals(version.getVersion())) {
            throw exception(BPM_DATA_SOURCE_UNPUBLISHED);
        }
        return new PublishedSource(version);
    }

    private static void validateBindings(LinkageNode node, Map<String, SchemaField> parameterSchema,
                                         Set<String> formFields) {
        for (String parameter : node.parameterBindings.keySet()) {
            if (RESERVED_PARAMETERS.contains(parameter)) {
                throw exception(BPM_DATA_SOURCE_PARAM_RESERVED, parameter);
            }
            if (!parameterSchema.containsKey(parameter)) {
                throw exception(BPM_DATA_SOURCE_CONFIG_INVALID);
            }
        }
        for (SchemaField parameter : parameterSchema.values()) {
            if (Boolean.TRUE.equals(parameter.getRequired()) && !RESERVED_PARAMETERS.contains(parameter.getName())
                    && !node.parameterBindings.containsKey(parameter.getName())) {
                throw exception(BPM_DATA_SOURCE_PARAM_MISSING, parameter.getName());
            }
        }

        // Keep this whitelist structurally identical to the runtime resolver. A FORM binding is meaningful only
        // when its root field is present and declared as a dependency in the same saved definition.
        for (String binding : node.parameterBindings.values()) {
            String rootType = validateBindingExpression(binding);
            if ("FORM".equals(rootType)) {
                String path = binding.substring("FORM.".length());
                String root = path.contains(".") ? path.substring(0, path.indexOf('.')) : path;
                if (!StringUtils.hasText(root) || !formFields.contains(root)) {
                    throw exception(BPM_DATA_SOURCE_CONFIG_INVALID);
                }
                if (!node.dependencies.contains(root)) {
                    throw exception(BPM_DATA_SOURCE_CONFIG_INVALID);
                }
            }
        }
    }

    private static void validateResults(LinkageNode node, BpmFormDataSourceVersionDO version,
                                        Map<String, SchemaField> resultSchema,
                                        Set<String> formFields) {
        String effectiveLabelField = StringUtils.hasText(node.labelField) ? node.labelField : version.getLabelField();
        String effectiveValueField = StringUtils.hasText(node.valueField) ? node.valueField : version.getValueField();
        if (node.pageable != null && !Objects.equals(node.pageable, version.getPageable())) {
            throw exception(BPM_DATA_SOURCE_CONFIG_INVALID);
        }
        if (!StringUtils.hasText(effectiveLabelField) || !resultSchema.containsKey(effectiveLabelField)
                || !StringUtils.hasText(effectiveValueField) || !resultSchema.containsKey(effectiveValueField)) {
            throw exception(BPM_DATA_SOURCE_RESULT_MAPPING_MISMATCH);
        }
        Set<String> targets = new HashSet<>();
        for (Map.Entry<String, String> mapping : node.outputMappings.entrySet()) {
            if (!resultSchema.containsKey(mapping.getKey()) || !formFields.contains(mapping.getValue())
                    || !targets.add(mapping.getValue())) {
                throw exception(BPM_DATA_SOURCE_RESULT_MAPPING_MISMATCH);
            }
        }
    }

    private static void validateDependencies(LinkageNode node, Set<String> formFields) {
        if (node.dependencies.stream().anyMatch(dependency -> !formFields.contains(dependency))) {
            throw exception(BPM_DATA_SOURCE_CONFIG_INVALID);
        }
    }

    private static String validateBindingExpression(String binding) {
        if (!BINDING_PATH.matcher(binding).matches()) {
            throw exception(BPM_DATA_SOURCE_CONFIG_INVALID);
        }
        String[] segments = binding.split("\\.");
        for (String segment : segments) {
            if (FORBIDDEN_BINDING_SEGMENTS.contains(segment)) {
                throw exception(BPM_DATA_SOURCE_CONFIG_INVALID);
            }
        }
        String root = segments[0];
        if ("USER".equals(root) && (segments.length != 2 || !USER_BINDING_FIELDS.contains(segments[1]))
                || "PROCESS".equals(root)
                && (segments.length != 2 || !PROCESS_BINDING_FIELDS.contains(segments[1]))) {
            throw exception(BPM_DATA_SOURCE_CONFIG_INVALID);
        }
        return root;
    }

    private static void validateUniqueOutputWriters(List<LinkageNode> nodes) {
        Map<String, String> writers = new HashMap<>();
        for (LinkageNode node : nodes) {
            for (String target : node.outputMappings.values()) {
                if (writers.putIfAbsent(target, node.field) != null) {
                    throw exception(BPM_DATA_SOURCE_RESULT_MAPPING_MISMATCH);
                }
            }
        }
    }

    private static void validateAcyclic(List<LinkageNode> nodes) {
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        nodes.forEach(node -> graph.computeIfAbsent(node.field, ignored -> new LinkedHashSet<>()));
        for (LinkageNode node : nodes) {
            // Dependency changes flow from upstream field to selector; selected output values flow from selector to
            // their mapped target. Both edge families belong to the same graph or a mapping back to an upstream
            // field would evade cycle detection.
            for (String dependency : node.dependencies) {
                graph.computeIfAbsent(dependency, ignored -> new LinkedHashSet<>()).add(node.field);
            }
            for (String target : node.outputMappings.values()) {
                graph.computeIfAbsent(node.field, ignored -> new LinkedHashSet<>()).add(target);
                graph.computeIfAbsent(target, ignored -> new LinkedHashSet<>());
            }
        }
        Map<String, VisitState> states = new HashMap<>();
        for (String field : graph.keySet()) {
            visit(field, graph, states);
        }
    }

    private static void visit(String field, Map<String, Set<String>> graph, Map<String, VisitState> states) {
        VisitState state = states.get(field);
        if (state == VisitState.GRAY) {
            throw exception(BPM_DATA_SOURCE_DEPENDENCY_CYCLE);
        }
        if (state == VisitState.BLACK) {
            return;
        }
        states.put(field, VisitState.GRAY);
        for (String dependency : graph.getOrDefault(field, Set.of())) {
            if (graph.containsKey(dependency)) {
                visit(dependency, graph, states);
            }
        }
        states.put(field, VisitState.BLACK);
    }

    private static Map<String, SchemaField> parseSchema(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            List<SchemaField> fields = JsonUtils.parseArray(json, SchemaField.class);
            Map<String, SchemaField> result = new LinkedHashMap<>();
            for (SchemaField field : fields) {
                if (!BpmFormDataSourceSchemaRules.isSafeFieldName(field.getName())
                        || result.putIfAbsent(field.getName(), field) != null) {
                    throw exception(BPM_DATA_SOURCE_CONFIG_INVALID);
                }
            }
            return result;
        } catch (ServiceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw exception(BPM_DATA_SOURCE_CONFIG_INVALID);
        }
    }

    private static Map<String, String> readStringMap(JsonNode props, String name) {
        JsonNode value = props.get(name);
        if (value == null || value.isNull()) {
            return Map.of();
        }
        if (!value.isObject()) {
            throw exception(BPM_DATA_SOURCE_CONFIG_INVALID);
        }
        Map<String, String> result = new LinkedHashMap<>();
        value.fields().forEachRemaining(entry -> {
            String text = text(entry.getValue());
            if (!StringUtils.hasText(entry.getKey()) || !StringUtils.hasText(text)) {
                throw exception(BPM_DATA_SOURCE_CONFIG_INVALID);
            }
            result.put(entry.getKey(), text);
        });
        return result;
    }

    private static List<String> readStringList(JsonNode props, String name) {
        JsonNode value = props.get(name);
        if (value == null || value.isNull()) {
            return List.of();
        }
        if (!value.isArray()) {
            throw exception(BPM_DATA_SOURCE_CONFIG_INVALID);
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (JsonNode item : value) {
            String text = text(item);
            if (!StringUtils.hasText(text) || !result.add(text)) {
                throw exception(BPM_DATA_SOURCE_CONFIG_INVALID);
            }
        }
        return List.copyOf(result);
    }

    private static String optionalText(JsonNode props, String name) {
        JsonNode value = props.get(name);
        if (value == null) {
            return null;
        }
        String text = text(value);
        if (!StringUtils.hasText(text)) {
            throw exception(BPM_DATA_SOURCE_CONFIG_INVALID);
        }
        return text;
    }

    private static Boolean optionalBoolean(JsonNode props, String name) {
        JsonNode value = props.get(name);
        if (value == null) {
            return null;
        }
        if (!value.isBoolean()) {
            throw exception(BPM_DATA_SOURCE_CONFIG_INVALID);
        }
        return value.booleanValue();
    }

    private static String firstText(JsonNode first, JsonNode second, String fallback) {
        String firstValue = text(first);
        if (StringUtils.hasText(firstValue)) {
            return firstValue;
        }
        String secondValue = text(second);
        return StringUtils.hasText(secondValue) ? secondValue : fallback;
    }

    private static String text(JsonNode value) {
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    private record LinkageNode(String field, String dataSourceCode, Map<String, String> parameterBindings,
                               List<String> dependencies, Map<String, String> outputMappings,
                               String labelField, String valueField, Boolean pageable) {
    }

    private record PublishedSource(BpmFormDataSourceVersionDO version) {
    }

    private enum VisitState {
        GRAY, BLACK
    }

    private static final class ParseContext {
        private final Map<String, String> fields = new LinkedHashMap<>();
        private final List<LinkageNode> linkageNodes = new ArrayList<>();
    }

    @Data
    private static class SchemaField {
        private String name;
        private String type;
        private Boolean required;
    }

}
