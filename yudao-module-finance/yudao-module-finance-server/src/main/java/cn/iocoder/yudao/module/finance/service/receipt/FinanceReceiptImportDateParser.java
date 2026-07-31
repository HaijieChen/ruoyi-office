package cn.iocoder.yudao.module.finance.service.receipt;

import cn.hutool.core.util.StrUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/**
 * 银行到款导入日期解析（档 1）。
 * <p>
 * 支持：
 * <ul>
 *   <li>{@code yyyy-MM-dd} → 当天 00:00:00</li>
 *   <li>{@code yyyy-MM-dd HH:mm:ss}</li>
 * </ul>
 * Excel 数值日期在读入阶段由 {@link FinanceReceiptImportDateStringConverter}
 * 规范为上述字符串之一，再经本类解析。
 */
public final class FinanceReceiptImportDateParser {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
            .withResolverStyle(ResolverStyle.STRICT);

    private FinanceReceiptImportDateParser() {
    }

    public static boolean isBlank(String raw) {
        return StrUtil.isBlank(raw);
    }

    /**
     * @return 解析成功返回 LocalDateTime；空白或无法解析返回 null
     */
    public static LocalDateTime tryParse(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        String value = raw.trim();
        try {
            if (value.length() == 10) {
                return LocalDate.parse(value, DATE).atStartOfDay();
            }
            if (value.length() == 19) {
                return LocalDateTime.parse(value, DATE_TIME);
            }
            return null;
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

}
