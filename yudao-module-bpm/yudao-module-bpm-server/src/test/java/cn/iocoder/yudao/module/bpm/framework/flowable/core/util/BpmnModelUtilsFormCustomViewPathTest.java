package cn.iocoder.yudao.module.bpm.framework.flowable.core.util;

import org.flowable.bpmn.model.UserTask;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BpmnModelUtilsFormCustomViewPathTest {

    @Test
    void parseFormCustomViewPathReadsExtensionText() {
        UserTask task = new UserTask();
        BpmnModelUtils.addExtensionElement(task, "formCustomViewPath",
                "/finance/salary-payment/detail/cashier");
        assertEquals("/finance/salary-payment/detail/cashier",
                BpmnModelUtils.parseFormCustomViewPath(task));
    }

    @Test
    void parseFormCustomViewPathBlankOrMissingIsNull() {
        assertNull(BpmnModelUtils.parseFormCustomViewPath(null));
        assertNull(BpmnModelUtils.parseFormCustomViewPath(new UserTask()));

        UserTask blank = new UserTask();
        BpmnModelUtils.addExtensionElement(blank, "formCustomViewPath", "  ");
        assertNull(BpmnModelUtils.parseFormCustomViewPath(blank));
    }
}
