package cn.iocoder.yudao.module.bpm.service.oa.listener;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEventListener;
import cn.iocoder.yudao.module.bpm.service.oa.BpmOAOutingService;
import cn.iocoder.yudao.module.bpm.service.oa.BpmOAOutingServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * OA 外出单的结果的监听器实现类
 */
@Component
public class BpmOAOutingStatusListener extends BpmProcessInstanceStatusEventListener {

    @Resource
    private BpmOAOutingService outingService;

    @Override
    protected String getProcessDefinitionKey() {
        return BpmOAOutingServiceImpl.PROCESS_KEY;
    }

    @Override
    protected void onEvent(BpmProcessInstanceStatusEvent event) {
        outingService.updateOutingStatus(Long.parseLong(event.getBusinessKey()),
                event.getProcessInstanceInfo().getStatus());
    }

}
