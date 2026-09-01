package cn.iocoder.yudao.module.bpm.service.task;

import org.flowable.engine.HistoryService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 已结束流程：带 includeProcessVariables 的历史联查可能漏行，
 * 审批全貌因此误报「流程实例不存在」。
 */
@ExtendWith(MockitoExtension.class)
class BpmProcessInstanceHistoricLookupTest {

    @Mock
    private HistoryService historyService;

    private BpmProcessInstanceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BpmProcessInstanceServiceImpl();
        ReflectionTestUtils.setField(service, "historyService", historyService);
    }

    @Test
    void findsFinishedInstanceWhenIncludeProcessVariablesJoinDropsRow() {
        String id = "61cf7294-a539-11f1-b04f-469fa1a325ed";
        HistoricProcessInstance finished = mock(HistoricProcessInstance.class);
        when(finished.getId()).thenReturn(id);

        HistoricProcessInstanceQuery withVarsQuery = mock(HistoricProcessInstanceQuery.class);
        HistoricProcessInstanceQuery withoutVarsQuery = mock(HistoricProcessInstanceQuery.class);
        when(historyService.createHistoricProcessInstanceQuery())
                .thenReturn(withVarsQuery, withoutVarsQuery);

        when(withVarsQuery.processInstanceId(id)).thenReturn(withVarsQuery);
        when(withVarsQuery.includeProcessVariables()).thenReturn(withVarsQuery);
        when(withVarsQuery.singleResult()).thenReturn(null);

        when(withoutVarsQuery.processInstanceId(id)).thenReturn(withoutVarsQuery);
        when(withoutVarsQuery.singleResult()).thenReturn(finished);
        when(historyService.createHistoricVariableInstanceQuery()).thenReturn(null);

        HistoricProcessInstance found = service.getHistoricProcessInstance(id);

        assertNotNull(found);
        assertEquals(id, found.getId());
    }
}
