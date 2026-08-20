package cn.iocoder.yudao.module.finance.service.expense;

import cn.hutool.core.util.StrUtil;

import java.math.BigDecimal;
import java.util.Set;

/** 北上广深 400，其他城市 300。 */
public final class FinanceStayCityCaps {

    public static final String TIER_T1 = "T1";
    public static final String TIER_OTHER = "OTHER";
    public static final BigDecimal CAP_T1 = new BigDecimal("400");
    public static final BigDecimal CAP_OTHER = new BigDecimal("300");

    private static final Set<String> T1_CITIES = Set.of("北京", "上海", "广州", "深圳");

    private FinanceStayCityCaps() {
    }

    public static String tier(String city) {
        if (StrUtil.isBlank(city)) {
            return null;
        }
        return T1_CITIES.contains(city.trim()) ? TIER_T1 : TIER_OTHER;
    }

    public static BigDecimal cap(String tier) {
        return TIER_T1.equals(tier) ? CAP_T1 : CAP_OTHER;
    }
}
