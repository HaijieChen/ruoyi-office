package cn.iocoder.yudao.module.bpm.service.definition;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDataSourceDO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDataSourceVersionDO;
import cn.iocoder.yudao.module.bpm.framework.datasource.BpmFormDataSourceQueryResult;

import java.util.Map;

/** Runtime execution of an immutable published form data-source version. */
public interface BpmFormDataSourceExecutionService {

    BpmFormDataSourceQueryResult execute(String code, Map<String, Object> requestParameters,
                                         LoginUser loginUser, String authorization,
                                         Long formId, String processInstanceId);

    /**
     * Executes an already loaded version. Lifecycle trial runs use this path with caching disabled, while retaining
     * the same parameter validation, result projection/masking, global limits and audit behavior as runtime calls.
     */
    BpmFormDataSourceQueryResult executeVersion(BpmFormDataSourceDO source, BpmFormDataSourceVersionDO version,
                                                Map<String, Object> requestParameters, LoginUser loginUser,
                                                String authorization, Long formId, String processInstanceId,
                                                boolean cacheEnabled);

}
