package cn.iocoder.yudao.module.bpm.framework.datasource;

import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.*;

/** SQL provider backed exclusively by the named read-only form data-source pool. */
@Component
public class BpmSqlDataSourceProvider implements BpmFormDataSourceProvider {

    private final ObjectProvider<BpmFormDataSourceExecutor> executorProvider;
    private final BpmFormDataSourceSqlValidator sqlValidator;
    private final BpmFormDataSourceProperties properties;

    public BpmSqlDataSourceProvider(ObjectProvider<BpmFormDataSourceExecutor> executorProvider,
                                    BpmFormDataSourceSqlValidator sqlValidator,
                                    BpmFormDataSourceProperties properties) {
        this.executorProvider = executorProvider;
        this.sqlValidator = sqlValidator;
        this.properties = properties;
    }

    @Override
    public int getType() {
        return TYPE_SQL;
    }

    @Override
    public BpmFormDataSourceQueryResult execute(BpmFormDataSourceExecutionContext context) {
        Map<String, Object> config = parseConfig(context.getVersion().getSourceConfig());
        String sql = config.get("sql") instanceof String value ? value : null;
        if (!StringUtils.hasText(sql)) {
            throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_CONFIG_INVALID);
        }
        Set<String> requiredNames = sqlValidator.validateAndExtractParameters(sql);
        for (String name : requiredNames) {
            if (!context.getParameters().containsKey(name)) {
                throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_PARAM_MISSING, name);
            }
        }
        BpmFormDataSourceExecutor executor = executorProvider.getIfAvailable();
        if (executor == null) {
            throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_CONFIG_INVALID);
        }
        int maxRows = bounded(context.getVersion().getMaxRows(), properties.getMaxRows());
        int timeout = bounded(context.getVersion().getTimeoutSeconds(), properties.getTimeoutSeconds());
        List<Map<String, Object>> rows = executor.execute(sql, context.getParameters(), maxRows, timeout);
        return new BpmFormDataSourceQueryResult(rows, rows.size(), context.getVersion().getVersion());
    }

    private static int bounded(Integer configured, int globalLimit) {
        int safeGlobal = Math.max(1, globalLimit);
        return configured == null ? safeGlobal : Math.max(1, Math.min(configured, safeGlobal));
    }

    private static Map<String, Object> parseConfig(String json) {
        Map<String, Object> result = JsonUtils.parseObjectQuietly(json, new TypeReference<>() {});
        if (result == null) {
            throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_CONFIG_INVALID);
        }
        return result;
    }

}
