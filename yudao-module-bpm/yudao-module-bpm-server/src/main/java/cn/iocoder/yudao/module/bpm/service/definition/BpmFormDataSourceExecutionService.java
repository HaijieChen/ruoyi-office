package cn.iocoder.yudao.module.bpm.service.definition;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.module.bpm.framework.datasource.BpmFormDataSourceQueryResult;

import java.util.Map;

/** Runtime execution of an immutable published form data-source version. */
public interface BpmFormDataSourceExecutionService {

    BpmFormDataSourceQueryResult execute(String code, Map<String, Object> requestParameters,
                                         LoginUser loginUser, String authorization,
                                         Long formId, String processInstanceId);

}
