package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmApprovalDetailRespVO.ActivityNodeTask;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BpmSequentialCandidateSliceTest {

    @Test
    void slicesAfterRunningTaskNotTheFirstEndedApprover() {
        ActivityNodeTask ended = new ActivityNodeTask();
        ended.setOwner(1L);
        ended.setAssignee(1L);
        ended.setStatus(BpmTaskStatusEnum.APPROVE.getStatus());
        ActivityNodeTask running = new ActivityNodeTask();
        running.setOwner(3L);
        running.setAssignee(3L);
        running.setStatus(BpmTaskStatusEnum.RUNNING.getStatus());

        List<Long> sliced = BpmProcessInstanceServiceImpl.sliceSequentialCandidatesAfterCurrent(
                List.of(1L, 2L, 3L, 4L), List.of(ended, running));

        assertEquals(List.of(4L), sliced);
    }
}
