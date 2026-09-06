package cn.iocoder.yudao.module.bpm.service.oa.listener;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEventListener;
import cn.iocoder.yudao.module.bpm.service.oa.BpmOAPunchCorrectionService;
import cn.iocoder.yudao.module.bpm.service.oa.BpmOAPunchCorrectionServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * OA 补卡单的结果的监听器实现类
 */
@Component
public class BpmOAPunchCorrectionStatusListener extends BpmProcessInstanceStatusEventListener {

    @Resource
    private BpmOAPunchCorrectionService punchCorrectionService;

    @Override
    protected String getProcessDefinitionKey() {
        return BpmOAPunchCorrectionServiceImpl.PROCESS_KEY;
    }

    @Override
    protected void onEvent(BpmProcessInstanceStatusEvent event) {
        punchCorrectionService.updatePunchCorrectionStatus(Long.parseLong(event.getBusinessKey()),
                event.getProcessInstanceInfo().getStatus(),
                event.getProcessInstanceId());
    }

}
