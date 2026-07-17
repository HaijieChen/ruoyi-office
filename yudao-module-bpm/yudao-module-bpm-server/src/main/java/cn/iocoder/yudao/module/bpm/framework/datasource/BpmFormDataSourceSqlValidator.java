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

        // 2. 词法守卫：分号、注释、INTO OUTFILE、危险函数
        if (LEXICAL_DENY_PATTERN.matcher(trimmed).find()) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.BPM_DATA_SOURCE_SQL_INVALID);
        }
        if (INTO_OUTFILE_PATTERN.matcher(trimmed).find()) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.BPM_DATA_SOURCE_SQL_INVALID);
        }
        if (DANGEROUS_FUNCTION_PATTERN.matcher(trimmed).find()) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.BPM_DATA_SOURCE_SQL_INVALID);
        }

        // 3. 提取命名参数（在 AST 解析前，因为 JSqlParser 可能不认 :param 语法）
        LinkedHashSet<String> parameters = new LinkedHashSet<>();
        Matcher paramMatcher = NAMED_PARAM_PATTERN.matcher(trimmed);
        while (paramMatcher.find()) {
            parameters.add(paramMatcher.group(1));
        }

        // 4. 将 :param 替换为 ? 以便 JSqlParser 能正确解析
        String normalizedSql = NAMED_PARAM_PATTERN.matcher(trimmed).replaceAll("?");

        // 5. AST 解析
        Statement statement;
        try {
            statement = CCJSqlParserUtil.parse(normalizedSql);
        } catch (Exception e) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.BPM_DATA_SOURCE_SQL_INVALID);
        }

        // 6. 只允许 SELECT（包括 WITH ... SELECT）
        if (!(statement instanceof Select)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.BPM_DATA_SOURCE_SQL_READ_ONLY);
        }

        return parameters;
    }
}
