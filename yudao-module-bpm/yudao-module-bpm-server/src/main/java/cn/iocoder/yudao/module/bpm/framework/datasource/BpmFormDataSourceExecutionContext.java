package cn.iocoder.yudao.module.bpm.framework.datasource;

import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDataSourceDO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDataSourceVersionDO;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

/** Immutable-by-convention provider input assembled on the server. */
@Data
@AllArgsConstructor
public class BpmFormDataSourceExecutionContext {

    private BpmFormDataSourceDO dataSource;
    private BpmFormDataSourceVersionDO version;
    private Map<String, Object> parameters;
    /** Forwarded only to the fixed internal platform API. Never logged or cached. */
    private String authorization;
    private Long tenantId;
    private Long userId;

}
