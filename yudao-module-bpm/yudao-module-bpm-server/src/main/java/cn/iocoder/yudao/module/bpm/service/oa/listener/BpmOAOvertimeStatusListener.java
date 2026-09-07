package cn.iocoder.yudao.module.bpm.service.oa.listener;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEventListener;
import cn.iocoder.yudao.module.bpm.service.oa.BpmOAOvertimeService;
import cn.iocoder.yudao.module.bpm.service.oa.BpmOAOvertimeServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * OA 加班单的结果的监听器实现类
 */
@Component
public class BpmOAOvertimeStatusListener extends BpmProcessInstanceStatusEventListener {

    @Resource
    private BpmOAOvertimeService overtimeService;

    @Override
    protected String getProcessDefinitionKey() {
        return BpmOAOvertimeServiceImpl.PROCESS_KEY;
    }

    @Override
    protected void onEvent(BpmProcessInstanceStatusEvent event) {
        overtimeService.updateOvertimeStatus(Long.parseLong(event.getBusinessKey()),
                event.getProcessInstanceInfo().getStatus(),
                event.getProcessInstanceId());
    }

}
