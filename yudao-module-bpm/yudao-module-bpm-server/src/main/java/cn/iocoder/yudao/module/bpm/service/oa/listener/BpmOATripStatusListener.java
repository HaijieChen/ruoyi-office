package cn.iocoder.yudao.module.bpm.service.oa.listener;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEventListener;
import cn.iocoder.yudao.module.bpm.service.oa.BpmOATripService;
import cn.iocoder.yudao.module.bpm.service.oa.BpmOATripServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * OA 出差单结果监听器
 */
@Component
public class BpmOATripStatusListener extends BpmProcessInstanceStatusEventListener {

    @Resource
    private BpmOATripService tripService;

    @Override
    protected String getProcessDefinitionKey() {
        return BpmOATripServiceImpl.PROCESS_KEY;
    }

    @Override
    protected void onEvent(BpmProcessInstanceStatusEvent event) {
        tripService.updateTripStatus(Long.parseLong(event.getBusinessKey()), event.getProcessInstanceInfo().getStatus());
    }

}
