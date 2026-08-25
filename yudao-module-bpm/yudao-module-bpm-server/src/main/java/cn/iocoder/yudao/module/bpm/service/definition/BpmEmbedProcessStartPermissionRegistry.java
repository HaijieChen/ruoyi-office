package cn.iocoder.yudao.module.bpm.service.definition;

import cn.hutool.core.util.StrUtil;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 嵌入式业务流程「发起所需业务权限」权威元数据（后端唯一真相源）。
 * <p>
 * 与前端 CREATE_SHELL_EMBED_REGISTRY 的 process key 对齐；缺权限配置视为禁止发起。
 * 不扩角色权限，仅声明业务 create 权限点，业务接口 @PreAuthorize 仍保留。
 */
public final class BpmEmbedProcessStartPermissionRegistry {

    private static final Map<String, Entry> REGISTRY;

    static {
        Map<String, Entry> map = new LinkedHashMap<>();
        map.put("finance_payment_apply", new Entry(
                "finance:payment-application:create",
                "无付款发起权限，请联系管理员分配付款发起角色"));
        // EXP-87：薪资/税金可在统一目录露出（有 create 时），但禁止通用 createProcessInstance 直启；
        // 点击目录卡片应跳转业务入口；独立菜单仍可用。
        map.put("finance_salary_payment_apply", new Entry(
                "finance:salary-payment:create",
                "请从「薪资付款申请」菜单或统一发起目录跳转业务入口发起，禁止通用流程直启"));
        map.put("finance_tax_payment_apply", new Entry(
                "finance:tax-payment:create",
                "请从「税金付款申请」菜单或统一发起目录跳转业务入口发起，禁止通用流程直启"));
        map.put("finance_contract_sign", new Entry(
                "finance:contract-application:create",
                "无合同签约发起权限，请联系管理员分配商务发起角色"));
        map.put("finance_invoice_apply", new Entry(
                "finance:invoice-application:create",
                "无开票发起权限，请联系管理员分配商务发起角色"));
        map.put("finance_invoice_redflush_apply", new Entry(
                "finance:invoice-application:create",
                "无开票红冲发起权限，请联系管理员分配商务发起角色"));
        REGISTRY = Collections.unmodifiableMap(map);
    }

    /**
     * 仅允许嵌入通用发起壳的 key（前端 CREATE_SHELL_EMBED_REGISTRY 对齐）。
     * 薪资/税金：目录可露出，但<strong>不</strong>走壳内嵌表单（跳转独立业务页）。
     */
    public static boolean isCreateShellEmbedAllowed(String processKey) {
        if (StrUtil.isBlank(processKey)) {
            return false;
        }
        String key = processKey.trim();
        if ("finance_salary_payment_apply".equals(key) || "finance_tax_payment_apply".equals(key)) {
            return false;
        }
        return isEmbedProcess(key);
    }

    /** 统一目录卡片点击后应 router 跳转的业务创建页 path（与前端 redirect-registry 对齐）。 */
    public static String catalogRedirectPath(String processKey) {
        if (StrUtil.isBlank(processKey)) {
            return null;
        }
        return switch (processKey.trim()) {
            case "finance_salary_payment_apply" -> "/finance/salary-payment";
            case "finance_tax_payment_apply" -> "/finance/tax-payment";
            default -> null;
        };
    }

    private BpmEmbedProcessStartPermissionRegistry() {
    }

    public static boolean isEmbedProcess(String processKey) {
        return StrUtil.isNotBlank(processKey) && REGISTRY.containsKey(processKey.trim());
    }

    public static Set<String> allEmbedKeys() {
        return REGISTRY.keySet();
    }

    public static String requiredPermission(String processKey) {
        Entry e = entry(processKey);
        return e == null ? null : e.requiredPermission();
    }

    public static String denyMessage(String processKey) {
        Entry e = entry(processKey);
        return e == null ? null : e.denyMessage();
    }

    public static Entry entry(String processKey) {
        if (StrUtil.isBlank(processKey)) {
            return null;
        }
        return REGISTRY.get(processKey.trim());
    }

    public record Entry(String requiredPermission, String denyMessage) {
    }
}
