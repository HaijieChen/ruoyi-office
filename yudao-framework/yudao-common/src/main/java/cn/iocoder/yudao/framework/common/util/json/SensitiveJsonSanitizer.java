package cn.iocoder.yudao.framework.common.util.json;

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.util.Iterator;
import java.util.Map;

/**
 * EXP-87 G2：共享 fail-closed body 脱敏器。
 * <p>
 * 覆盖 access-log 落库、error-log 落库、非 prod 控制台日志、网关 access log 等所有写 body 的 sink。
 * 解析失败时禁止回传原文（可能含 accountNo）。
 */
@Slf4j
public final class SensitiveJsonSanitizer {

    /** 解析失败 fail-closed 占位 */
    public static final String REDACTED_BODY = "{\"__redacted\":true,\"reason\":\"sanitize_failed\"}";

    /** 默认脱敏键：认证凭据 + 银行账号类字段 */
    public static final String[] DEFAULT_SANITIZE_KEYS = new String[]{
            "password", "token", "accessToken", "refreshToken",
            "accountNo", "account_no", "payeeBankAccount", "payee_bank_account",
            "bankAccount", "bank_account", "accountNoSnapshot", "account_no_snapshot"
    };

    private SensitiveJsonSanitizer() {
    }

    /**
     * 脱敏 JSON 字符串；失败则返回 {@link #REDACTED_BODY}，并仅按 length 记 error log。
     *
     * @param jsonString   原始 body
     * @param extraKeys    额外脱敏键（可 null）
     * @return 脱敏后 JSON，或 REDACTED_BODY；空输入返回 null
     */
    public static String sanitize(String jsonString, String[] extraKeys) {
        if (StrUtil.isEmpty(jsonString)) {
            return null;
        }
        try {
            JsonNode rootNode = JsonUtils.getObjectMapper().readTree(jsonString);
            sanitizeNode(rootNode, extraKeys);
            return JsonUtils.toJsonString(rootNode);
        } catch (Exception e) {
            log.error("[SensitiveJsonSanitizer][脱敏失败，已丢弃 body，length={}]", jsonString.length());
            return REDACTED_BODY;
        }
    }

    /**
     * 脱敏；无额外键。
     */
    public static String sanitize(String jsonString) {
        return sanitize(jsonString, null);
    }

    /**
     * 对已解析的 Map 风格 JSON 对象字符串做脱敏（网关等可能传入非严格 JSON 时仍 fail-closed）。
     */
    public static String sanitizeOrRedact(String raw) {
        if (StrUtil.isEmpty(raw)) {
            return raw;
        }
        // 非 JSON 也不得原样输出（可能夹带 accountNo）
        if (!JsonUtils.isJson(raw)) {
            if (containsSensitiveLiteralHint(raw)) {
                log.error("[SensitiveJsonSanitizer][非 JSON body 疑似敏感，已丢弃，length={}]", raw.length());
                return REDACTED_BODY;
            }
            return raw;
        }
        return sanitize(raw, null);
    }

    private static boolean containsSensitiveLiteralHint(String raw) {
        String lower = raw.toLowerCase();
        return lower.contains("accountno") || lower.contains("account_no")
                || lower.contains("bankaccount") || lower.contains("password")
                || lower.contains("\"token\"");
    }

    private static void sanitizeNode(JsonNode node, String[] extraKeys) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                sanitizeNode(child, extraKeys);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        ObjectNode objectNode = (ObjectNode) node;
        Iterator<Map.Entry<String, JsonNode>> iterator = objectNode.properties().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = iterator.next();
            String key = entry.getKey();
            if (ArrayUtil.contains(DEFAULT_SANITIZE_KEYS, key)
                    || (extraKeys != null && ArrayUtil.contains(extraKeys, key))) {
                iterator.remove();
                continue;
            }
            sanitizeNode(entry.getValue(), extraKeys);
        }
    }
}
