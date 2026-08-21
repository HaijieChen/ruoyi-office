package cn.iocoder.yudao.module.hrm.service.payroll;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PayrollBatchRules {

    public static final String DRAFT = "DRAFT";
    public static final String PUBLISHED = "PUBLISHED";

    public static boolean canEdit(String status) {
        return DRAFT.equals(status);
    }

    public static String publish(String status) {
        if (!DRAFT.equals(status)) {
            throw new IllegalStateException("only draft can publish");
        }
        return PUBLISHED;
    }

    public static String withdraw(String status) {
        if (!PUBLISHED.equals(status)) {
            throw new IllegalStateException("only published can withdraw");
        }
        return DRAFT;
    }

    public static boolean uniqueExactName(List<String> archiveNames, String punchName) {
        if (punchName == null) {
            return false;
        }
        return archiveNames.stream().filter(punchName::equals).count() == 1;
    }

    public static BigDecimal yearToDateSick(BigDecimal opening, List<BigDecimal> publishedMonths, BigDecimal uncoveredOaSick) {
        BigDecimal sum = opening == null ? BigDecimal.ZERO : opening;
        if (publishedMonths != null) {
            for (BigDecimal m : publishedMonths) {
                if (m != null) {
                    sum = sum.add(m);
                }
            }
        }
        if (uncoveredOaSick != null) {
            sum = sum.add(uncoveredOaSick);
        }
        return sum;
    }

    public static Map<String, Long> nameCounts(List<String> names) {
        return names.stream().collect(Collectors.groupingBy(n -> n, Collectors.counting()));
    }
}
