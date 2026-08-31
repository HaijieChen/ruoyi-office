package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BpmGroupedEndActivityNodeStatusTest {

    @Test
    void orSignKeepsApproveWhenLaterSiblingIsCancelled() {
        HistoricTaskInstance approved = task(BpmTaskStatusEnum.APPROVE.getStatus(), new Date(1_000));
        HistoricTaskInstance cancelled = task(BpmTaskStatusEnum.CANCEL.getStatus(), new Date(2_000));

        Integer status = BpmProcessInstanceServiceImpl.getGroupedEndActivityNodeStatus(
                List.of(approved, cancelled));

        assertEquals(BpmTaskStatusEnum.APPROVE.getStatus(), status);
    }

    @Test
    void rejectStillWinsOverApprove() {
        HistoricTaskInstance approved = task(BpmTaskStatusEnum.APPROVE.getStatus(), new Date(2_000));
        HistoricTaskInstance rejected = task(BpmTaskStatusEnum.REJECT.getStatus(), new Date(1_000));

        Integer status = BpmProcessInstanceServiceImpl.getGroupedEndActivityNodeStatus(
                List.of(approved, rejected));

        assertEquals(BpmTaskStatusEnum.REJECT.getStatus(), status);
    }

    @Test
    void allCancelledStaysCancelled() {
        HistoricTaskInstance first = task(BpmTaskStatusEnum.CANCEL.getStatus(), new Date(1_000));
        HistoricTaskInstance last = task(BpmTaskStatusEnum.CANCEL.getStatus(), new Date(2_000));

        Integer status = BpmProcessInstanceServiceImpl.getGroupedEndActivityNodeStatus(
                List.of(first, last));

        assertEquals(BpmTaskStatusEnum.CANCEL.getStatus(), status);
    }

    private static HistoricTaskInstance task(Integer status, Date endTime) {
        HistoricTaskInstance historicTask = mock(HistoricTaskInstance.class);
        when(historicTask.getEndTime()).thenReturn(endTime);
        when(historicTask.getTaskLocalVariables()).thenReturn(
                Map.of(BpmnVariableConstants.TASK_VARIABLE_STATUS, status));
        return historicTask;
    }
}
