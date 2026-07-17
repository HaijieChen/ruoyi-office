package cn.iocoder.yudao.module.bpm.service.definition;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.datasource.BpmFormDataSourcePageReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.datasource.BpmFormDataSourceSaveReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.datasource.BpmFormDataSourceVersionSaveReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDataSourceDO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDataSourceVersionDO;
import cn.iocoder.yudao.module.bpm.framework.datasource.BpmFormDataSourceQueryResult;

import java.util.List;
import java.util.Map;

/** Lifecycle operations for versioned dynamic-form data sources. */
public interface BpmFormDataSourceService {

    Long createDataSource(BpmFormDataSourceSaveReqVO reqVO);

    void updateDataSource(BpmFormDataSourceSaveReqVO reqVO);

    BpmFormDataSourceDO getDataSource(Long id);

    PageResult<BpmFormDataSourceDO> getDataSourcePage(BpmFormDataSourcePageReqVO reqVO);

    List<BpmFormDataSourceDO> getSimpleDataSourceList();

    List<BpmFormDataSourceVersionDO> getVersionList(Long sourceId);

    BpmFormDataSourceVersionDO getVersion(Long sourceId, Long versionId);

    Long saveDraft(Long sourceId, BpmFormDataSourceVersionSaveReqVO reqVO);

    BpmFormDataSourceQueryResult trialRun(Long sourceId, Long versionId, Map<String, Object> parameters,
                                          LoginUser loginUser, String authorization);

    void publish(Long sourceId, Long versionId);

    void disable(Long id);

}
