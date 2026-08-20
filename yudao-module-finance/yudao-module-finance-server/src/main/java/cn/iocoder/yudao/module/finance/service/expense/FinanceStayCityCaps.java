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

    /** 奇数 (n+1)/2 间，偶数 n/2 间。 */
    public static int roomsForSameGender(int count) {
        if (count <= 0) {
            return 0;
        }
        return count % 2 == 0 ? count / 2 : (count + 1) / 2;
    }

    /** 男女分开算房间；性别未知每人一间。sex：1男 2女。 */
    public static int rooms(java.util.Collection<Integer> sexes) {
        int male = 0;
        int female = 0;
        int unknown = 0;
        if (sexes != null) {
            for (Integer sex : sexes) {
                if (sex != null && sex == 1) {
                    male++;
                } else if (sex != null && sex == 2) {
                    female++;
                } else {
                    unknown++;
                }
            }
        }
        return roomsForSameGender(male) + roomsForSameGender(female) + unknown;
    }

    public static int nights(java.time.LocalDate start, java.time.LocalDate end) {
        if (start == null || end == null) {
            return 1;
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(start, end);
        return (int) Math.max(1L, days);
    }

    public static BigDecimal stayCap(String tier, int rooms, int nights) {
        int r = Math.max(rooms, 1);
        int n = Math.max(nights, 1);
        return cap(tier).multiply(java.math.BigDecimal.valueOf(r)).multiply(java.math.BigDecimal.valueOf(n));
    }
}
