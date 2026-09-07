package cn.iocoder.yudao.module.bpm.controller.admin.task;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BpmProcessInstanceApprovalDetailAuthTest {

    @Test
    void approvalDetailGetDoesNotRequireProcessInstanceQueryMenu() throws Exception {
        String src = Files.readString(Path.of(
                "src/main/java/cn/iocoder/yudao/module/bpm/controller/admin/task/BpmProcessInstanceController.java"));
        int idx = src.indexOf("@GetMapping(\"/get-approval-detail\")");
        assertTrue(idx >= 0);
        String block = src.substring(idx, src.indexOf("@GetMapping(\"/get-next-approval-nodes\")"));
        assertFalse(block.contains("@ss.hasPermission('bpm:process-instance:query')"));
        assertTrue(block.contains("isAuthenticated()"));
        assertTrue(src.contains("assertCanPreviewOrViewApproval"));
        assertTrue(src.contains("定义预览需要流程查询权限"));
    }

    @Test
    void processInstanceTaskListDoesNotRequireTaskQueryMenu() throws Exception {
        String src = Files.readString(Path.of(
                "src/main/java/cn/iocoder/yudao/module/bpm/controller/admin/task/BpmTaskController.java"));
        int idx = src.indexOf("@GetMapping(\"/list-by-process-instance-id\")");
        assertTrue(idx >= 0);
        String block = src.substring(idx, src.indexOf("@PutMapping(\"/approve\")"));
        assertFalse(block.contains("bpm:task:query"));
        assertTrue(block.contains("isAuthenticated()"));
        assertTrue(block.contains("assertCanViewDetail"));
        assertTrue(src.contains("@PreAuthorize(\"@ss.hasPermission('bpm:task:manager-query')\")"));
        assertTrue(src.contains("@GetMapping(\"/list-by-parent-task-id\")") || src.contains("list-by-parent-task-id")
                || src.contains("bpm:task:update"));
    }
}
