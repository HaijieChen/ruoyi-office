package cn.iocoder.yudao.module.bpm.framework.im;

import cn.iocoder.yudao.module.bpm.service.message.dto.BpmMessageSendWhenTaskCreatedReqDTO;

/**
 * IM 工作通知：任务分配给审批人，运行中进度给发起人。超管与未绑定不发。
 */
public interface ImWorkNoticeService {

    int notifyTaskAssigned(BpmMessageSendWhenTaskCreatedReqDTO reqDTO);
}
