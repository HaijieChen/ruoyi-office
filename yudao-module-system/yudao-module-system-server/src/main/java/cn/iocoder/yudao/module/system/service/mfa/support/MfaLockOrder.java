package cn.iocoder.yudao.module.system.service.mfa.support;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * ADR-MFA-v3 §6.1 权威行锁顺序（切片 1 冻结骨架，供 CE-01/02 与切片 2 使用）。
 * <p>
 * {@code GLOBAL policy → TENANT policy → USER assurance → FACTOR → FLOW/DECISION/CODE → TOKEN family}
 */
public final class MfaLockOrder {

    public enum Resource {
        GLOBAL_POLICY(1),
        TENANT_POLICY(2),
        USER_ASSURANCE(3),
        FACTOR(4),
        FLOW_OR_DECISION_OR_CODE(5),
        TOKEN_FAMILY(6);

        private final int order;

        Resource(int order) {
            this.order = order;
        }

        public int order() {
            return order;
        }
    }

    public static final List<Resource> CANONICAL_ORDER =
            Collections.unmodifiableList(Arrays.asList(Resource.values()));

    private MfaLockOrder() {
    }

    /**
     * 校验调用方请求的锁顺序是否非降序（允许子集，但相对顺序必须符合 CANONICAL_ORDER）。
     */
    public static boolean isValidOrder(List<Resource> requested) {
        if (requested == null || requested.isEmpty()) {
            return true;
        }
        int prev = 0;
        for (Resource r : requested) {
            if (r == null) {
                return false;
            }
            if (r.order() < prev) {
                return false;
            }
            prev = r.order();
        }
        return true;
    }

    public static void requireValidOrder(List<Resource> requested) {
        if (!isValidOrder(requested)) {
            throw new IllegalStateException("MFA lock order violation: " + requested
                    + "; required non-decreasing along " + CANONICAL_ORDER);
        }
    }
}
