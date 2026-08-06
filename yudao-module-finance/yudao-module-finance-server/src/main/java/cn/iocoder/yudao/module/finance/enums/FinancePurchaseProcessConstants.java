package cn.iocoder.yudao.module.finance.enums;

import java.util.List;
import java.util.Set;

/**
 * 通用采购流程 processDefinitionKey 白名单（PAY-P0-2 / TP9）。
 *
 * <p>付款前置引用与选择器共用；禁止散落硬编码不一致的 key。
 * 采购 = OA 通用审批流，非 finance 专表。
 */
public final class FinancePurchaseProcessConstants {

    /** 318 配置验收记录中的采购申请 key */
    public static final String OA_PURCHASE_APPLY = "oa_purchase_apply";

    /**
     * 可被付款引用的采购流程 key 白名单（唯一真相源）。
     */
    public static final List<String> PURCHASE_PROCESS_KEYS = List.of(OA_PURCHASE_APPLY);

    public static final Set<String> PURCHASE_PROCESS_KEY_SET = Set.copyOf(PURCHASE_PROCESS_KEYS);

    private FinancePurchaseProcessConstants() {
    }

    public static boolean isPurchaseProcessKey(String processDefinitionKey) {
        return processDefinitionKey != null && PURCHASE_PROCESS_KEY_SET.contains(processDefinitionKey);
    }

}
