package cn.iocoder.yudao.module.bpm.framework.datasource;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BPM 动态表单数据源 — SQL 安全校验器
 *
 * 使用 JSqlParser AST 解析 + 显式词法守卫，确保仅允许安全的 SELECT 查询。
 * 绝不记录原始参数值。
 */
public class BpmFormDataSourceSqlValidator {

    /**
     * 词法层面检测：分号（多语句）、单行注释（-- 和 #）、块注释（/*）
     */
    private static final Pattern LEXICAL_DENY_PATTERN = Pattern.compile(
            ";|--\\s|--$|#|/\\*"
    );

    /**
     * 词法层面检测 INTO OUTFILE / INTO DUMPFILE（不区分大小写）
     */
    private static final Pattern INTO_OUTFILE_PATTERN = Pattern.compile(
            "\\bINTO\\s+(OUTFILE|DUMPFILE)\\b", Pattern.CASE_INSENSITIVE
    );

    /**
     * 危险函数词法检测（不区分大小写，匹配函数调用形式 name(）
     */
    private static final Pattern DANGEROUS_FUNCTION_PATTERN = Pattern.compile(
            "\\b(LOAD_FILE|SLEEP|BENCHMARK|GET_LOCK|RELEASE_LOCK)\\s*\\(", Pattern.CASE_INSENSITIVE
    );

    /**
     * 提取 :namedParameter 的正则（冒号后跟标识符）
     */
    private static final Pattern NAMED_PARAM_PATTERN = Pattern.compile(
            ":([a-zA-Z_][a-zA-Z0-9_]*)"
    );

    /**
     * 校验 SQL 安全性并提取命名参数
     *
     * @param sql 管理员配置的 SQL
     * @return 提取的命名参数集合（去重、稳定顺序）
     * @throws ServiceException 当 SQL 不合法时
     */
    public Set<String> validateAndExtractParameters(String sql) {
        // 1. 空白校验
        if (sql == null || sql.isBlank()) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.BPM_DATA_SOURCE_SQL_INVALID);
        }

        String trimmed = sql.trim();

        // 2. 掩码字符串字面量：将单引号内的内容替换为空格，
        //    这样词法守卫和参数提取只扫描 SQL 结构部分，不会被字面量干扰。
        //    支持 '' 转义（SQL 标准的单引号转义方式）。
        String masked = maskStringLiterals(trimmed);

        // 3. 词法守卫：分号、注释、INTO OUTFILE、危险函数（在掩码后的 SQL 上检测）
        if (LEXICAL_DENY_PATTERN.matcher(masked).find()) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.BPM_DATA_SOURCE_SQL_INVALID);
        }
        if (INTO_OUTFILE_PATTERN.matcher(masked).find()) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.BPM_DATA_SOURCE_SQL_INVALID);
        }
        if (DANGEROUS_FUNCTION_PATTERN.matcher(masked).find()) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.BPM_DATA_SOURCE_SQL_INVALID);
        }

        // 4. 提取命名参数（在掩码后的 SQL 上提取，避免字面量中的 :phantom 参数）
        LinkedHashSet<String> parameters = new LinkedHashSet<>();
        Matcher paramMatcher = NAMED_PARAM_PATTERN.matcher(masked);
        while (paramMatcher.find()) {
            parameters.add(paramMatcher.group(1));
        }

        // 5. 将 :param 替换为 ? 以便 JSqlParser 能正确解析
        String normalizedSql = NAMED_PARAM_PATTERN.matcher(trimmed).replaceAll("?");

        // 6. AST 解析
        Statement statement;
        try {
            statement = CCJSqlParserUtil.parse(normalizedSql);
        } catch (Exception e) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.BPM_DATA_SOURCE_SQL_INVALID);
        }

        // 7. 只允许 SELECT（包括 WITH ... SELECT）
        if (!(statement instanceof Select)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.BPM_DATA_SOURCE_SQL_READ_ONLY);
        }

        return parameters;
    }

    /**
     * 将 SQL 中单引号括起来的字符串字面量内容替换为空格，保留引号本身。
     * 支持 SQL 标准的 '' 转义（两个连续单引号表示一个字面量单引号）。
     * <p>
     * 例如: {@code SELECT * FROM t WHERE name = 'hello -- world'}
     * 变为: {@code SELECT * FROM t WHERE name = '               '}
     * <p>
     * 这样词法守卫在掩码后的字符串上工作，不会误判字面量中的内容。
     *
     * @param sql 原始 SQL
     * @return 字面量内容被空格替换后的 SQL
     */
    static String maskStringLiterals(String sql) {
        StringBuilder sb = new StringBuilder(sql.length());
        boolean inString = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'') {
                if (inString) {
                    // 检查是否为 '' 转义
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                        // '' 转义：用两个空格替代，保持位置对齐
                        sb.append("  ");
                        i++; // 跳过下一个引号
                        continue;
                    }
                    // 字符串结束
                    inString = false;
                }  else {
                    // 字符串开始
                    inString = true;
                }
                sb.append(c); // 保留引号本身
            } else if (inString) {
                sb.append(' '); // 替换字面量内容为空格
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
