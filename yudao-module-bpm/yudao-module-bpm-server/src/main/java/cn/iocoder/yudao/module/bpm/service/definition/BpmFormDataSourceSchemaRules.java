package cn.iocoder.yudao.module.bpm.service.definition;

import java.util.regex.Pattern;
import java.util.Set;

/** Shared field-name contract for persisted schemas, runtime parameters and frontend result access. */
final class BpmFormDataSourceSchemaRules {

    // label_field/value_field are varchar(63), so the shared schema contract must not exceed 63 characters.
    private static final Pattern SAFE_FIELD_NAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,62}$");
    private static final Set<String> FORBIDDEN_FIELD_NAMES = Set.of("__proto__", "constructor", "prototype");

    private BpmFormDataSourceSchemaRules() {
    }

    static boolean isSafeFieldName(String value) {
        return value != null && SAFE_FIELD_NAME.matcher(value).matches() && !FORBIDDEN_FIELD_NAMES.contains(value);
    }

}
