package cn.iocoder.yudao.module.bpm.dal.mysql.task;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.bpm.dal.dataobject.task.BpmProcessInstanceShareDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface BpmProcessInstanceShareMapper extends BaseMapperX<BpmProcessInstanceShareDO> {

    default BpmProcessInstanceShareDO selectByInstanceAndRecipient(String processInstanceId, Long recipientUserId) {
        return selectOne(new LambdaQueryWrapperX<BpmProcessInstanceShareDO>()
                .eq(BpmProcessInstanceShareDO::getProcessInstanceId, processInstanceId)
                .eq(BpmProcessInstanceShareDO::getRecipientUserId, recipientUserId));
    }

    default List<BpmProcessInstanceShareDO> selectActiveByInstance(String processInstanceId) {
        return selectList(new LambdaQueryWrapperX<BpmProcessInstanceShareDO>()
                .eq(BpmProcessInstanceShareDO::getProcessInstanceId, processInstanceId)
                .isNull(BpmProcessInstanceShareDO::getRevokedAt)
                .orderByDesc(BpmProcessInstanceShareDO::getId));
    }

    default PageResult<BpmProcessInstanceShareDO> selectActivePageByRecipient(Long recipientUserId, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<BpmProcessInstanceShareDO>()
                .eq(BpmProcessInstanceShareDO::getRecipientUserId, recipientUserId)
                .isNull(BpmProcessInstanceShareDO::getRevokedAt)
                .orderByDesc(BpmProcessInstanceShareDO::getId));
    }

    default List<BpmProcessInstanceShareDO> selectActiveByRecipient(Long recipientUserId) {
        return selectList(new LambdaQueryWrapperX<BpmProcessInstanceShareDO>()
                .eq(BpmProcessInstanceShareDO::getRecipientUserId, recipientUserId)
                .isNull(BpmProcessInstanceShareDO::getRevokedAt));
    }
}
