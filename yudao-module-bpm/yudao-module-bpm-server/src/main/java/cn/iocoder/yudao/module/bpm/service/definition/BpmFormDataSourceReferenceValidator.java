package cn.iocoder.yudao.module.bpm.service.definition;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDO;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.BPM_DATA_SOURCE_FORM_NOT_REFERENCED;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.FORM_NOT_EXISTS;

/**
 * Validates that a runtime data-source call is bound to a component in a trusted persisted form.
 *
 * <p>The parser deliberately recognizes only the component and property path produced by this
 * application's form-create designer. It must not be replaced by a JSON string search because an
 * unrelated label, option or component property could otherwise grant access to a data source.</p>
 */
@Component
@RequiredArgsConstructor
public class BpmFormDataSourceReferenceValidator {

    static final String REMOTE_COMPONENT_TYPE = "RemoteDataSourceSelect";
    private static final Set<String> SCOPED_RULE_COMPONENTS = Set.of("group", "subForm");

    private final BpmFormService formService;

    /**
     * Validate an exact data-source reference in the persisted form identified by {@code formId}.
     *
     * @param formId trusted persisted BPM form identifier
     * @param sourceCode published data-source code requested by the caller
     */
    public void validateFormReference(Long formId, String sourceCode) {
        if (formId == null) {
            throw exception(FORM_NOT_EXISTS);
        }
        BpmFormDO form = formService.getForm(formId);
        if (form == null) {
            throw exception(FORM_NOT_EXISTS);
        }
        if (sourceCode == null || sourceCode.isBlank()) {
            throw exception(BPM_DATA_SOURCE_FORM_NOT_REFERENCED);
        }

        ReferenceParseResult parseResult = parseReferences(form.getFields());
        if (!parseResult.valid() || parseResult.references().stream()
                .noneMatch(reference -> sourceCode.equals(reference.sourceCode()))) {
            throw exception(BPM_DATA_SOURCE_FORM_NOT_REFERENCED);
        }
    }

    /**
     * Parse references from decoded form-create field JSON.
     *
     * <p>Package visibility is intentional: Task 5's save-time linkage validator can reuse the
     * same strict component recognition without widening the runtime API.</p>
     */
    static ReferenceParseResult parseReferences(List<String> fields) {
        if (fields == null || fields.isEmpty()) {
            return ReferenceParseResult.valid(List.of());
        }
        List<FormDataSourceReference> references = new ArrayList<>();
        for (String fieldJson : fields) {
            JsonNode root = readObject(fieldJson);
            if (root == null) {
                return ReferenceParseResult.invalid();
            }
            walkRules(root, (node, scoped) -> collectReference(node, references));
        }
        return ReferenceParseResult.valid(references);
    }

    static JsonNode readObject(String fieldJson) {
        if (fieldJson == null || fieldJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = JsonUtils.getObjectMapper().reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(fieldJson);
            return root != null && root.isObject() ? root : null;
        } catch (Exception ignored) {
            // Fail closed. Do not log the whole form definition because component properties can
            // contain internal values and the runtime caller cannot repair persisted form JSON.
            return null;
        }
    }

    private static void collectReference(JsonNode node, List<FormDataSourceReference> references) {
        JsonNode typeNode = node.get("type");
        JsonNode fieldNode = node.get("field");
        JsonNode propsNode = node.get("props");
        if (isText(typeNode, REMOTE_COMPONENT_TYPE)
                && isNonBlankText(fieldNode)
                && propsNode != null && propsNode.isObject()) {
            JsonNode sourceCodeNode = propsNode.get("dataSourceCode");
            if (isNonBlankText(sourceCodeNode)) {
                references.add(new FormDataSourceReference(fieldNode.textValue(), sourceCodeNode.textValue()));
            }
        }

    }

    /** Walk the actual rule containers emitted by FcDesigner {@code getRule()}. */
    static void walkRules(JsonNode root, RuleVisitor visitor) {
        walkRuleContainer(root, false, visitor);
    }

    private static void walkRuleContainer(JsonNode container, boolean scoped, RuleVisitor visitor) {
        if (container == null) {
            return;
        }
        if (container.isArray()) {
            container.forEach(item -> walkRuleContainer(item, scoped, visitor));
            return;
        }
        if (!container.isObject()) {
            // Text/HTML/button children are valid form-create leaves and never represent a field rule.
            return;
        }

        visitor.visit(container, scoped);
        walkRuleContainer(container.get("children"), scoped, visitor);

        String type = text(container.get("type"));
        JsonNode props = container.get("props");
        if (props != null && props.isObject()) {
            if (SCOPED_RULE_COMPONENTS.contains(type)) {
                walkRuleContainer(props.get("rule"), true, visitor);
            }
            if ("tableForm".equals(type)) {
                JsonNode columns = props.get("columns");
                if (columns != null && columns.isArray()) {
                    columns.forEach(column -> {
                        if (column.isObject()) {
                            walkRuleContainer(column.get("rule"), true, visitor);
                        }
                    });
                }
            }
        }

        JsonNode controls = container.get("control");
        if (controls != null && controls.isArray()) {
            controls.forEach(control -> {
                if (control.isObject()) {
                    walkRuleContainer(control.get("rule"), scoped, visitor);
                }
            });
        }
    }

    private static boolean isText(JsonNode node, String expected) {
        return node != null && node.isTextual() && expected.equals(node.textValue());
    }

    private static boolean isNonBlankText(JsonNode node) {
        return node != null && node.isTextual() && !node.textValue().isBlank();
    }

    private static String text(JsonNode node) {
        return node != null && node.isTextual() ? node.textValue() : null;
    }

    @FunctionalInterface
    interface RuleVisitor {
        void visit(JsonNode node, boolean scoped);
    }

    record FormDataSourceReference(String field, String sourceCode) {
    }

    record ReferenceParseResult(boolean valid, List<FormDataSourceReference> references) {

        private static ReferenceParseResult valid(List<FormDataSourceReference> references) {
            return new ReferenceParseResult(true,
                    Collections.unmodifiableList(new ArrayList<>(references)));
        }

        private static ReferenceParseResult invalid() {
            return new ReferenceParseResult(false, List.of());
        }

    }

}
