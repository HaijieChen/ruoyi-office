package cn.iocoder.yudao.module.finance.service.common;

import cn.hutool.core.util.StrUtil;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.CURRENCY_INVALID;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.CURRENCY_MISMATCH;

/**
 * EXP-73：交易币种白名单与同币种约束（不做汇率折算）。
 */
public final class FinanceCurrencySupport {

    public static final Set<String> SUPPORTED = Set.of("CNY", "USD", "HKD");

    private FinanceCurrencySupport() {
    }

    /**
     * 规范化交易币种：trim + upper；仅允许 CNY/USD/HKD。
     */
    public static String requireSupported(String raw) {
        if (StrUtil.isBlank(raw)) {
            throw exception(CURRENCY_INVALID);
        }
        String currency = raw.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED.contains(currency)) {
            throw exception(CURRENCY_INVALID);
        }
        return currency;
    }

    /**
     * 可选币种：空则返回 null（历史兼容）；非空则强校验。
     */
    public static String normalizeOptional(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        return requireSupported(raw);
    }

    /**
     * 关联单据同币种：双方均有币种且不一致时拒绝；任一方历史空则放行。
     */
    public static void assertSameIfBothPresent(String sourceCurrency, String targetCurrency) {
        String a = StrUtil.blankToDefault(sourceCurrency, null);
        String b = StrUtil.blankToDefault(targetCurrency, null);
        if (a == null || b == null) {
            return;
        }
        if (!Objects.equals(a.trim().toUpperCase(Locale.ROOT), b.trim().toUpperCase(Locale.ROOT))) {
            throw exception(CURRENCY_MISMATCH);
        }
    }
}
