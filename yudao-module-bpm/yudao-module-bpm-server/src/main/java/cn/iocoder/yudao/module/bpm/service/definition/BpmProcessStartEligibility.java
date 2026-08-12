package cn.iocoder.yudao.module.bpm.service.definition;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流程发起资格（列表 / 详情 / 启动预检统一结构）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BpmProcessStartEligibility {

    /** 当前登录用户是否可在发起列表展示并进入表单 */
    private boolean canStart;

    /** 嵌入式流程要求的业务权限；非嵌入式为 null */
    private String requiredStartPermission;

    /** 不可发起时的友好原因（前端直接展示，勿再拼通用 403） */
    private String cannotStartReason;

    public static BpmProcessStartEligibility allowed() {
        return BpmProcessStartEligibility.builder().canStart(true).build();
    }

    public static BpmProcessStartEligibility denied(String requiredStartPermission, String reason) {
        return BpmProcessStartEligibility.builder()
                .canStart(false)
                .requiredStartPermission(requiredStartPermission)
                .cannotStartReason(reason)
                .build();
    }
}
