package cn.iocoder.yudao.module.bpm.framework.im;

import lombok.Data;

@Data
public class ImCardTaskSnapshot {

    private String taskId;
    private String processInstanceId;
    private Long assigneeUserId;
    private Long startUserId;
    private Boolean signEnable;
    private Boolean reasonRequire;
    private String nodeFormCustomViewPath;
    private Boolean approveButtonEnabled = true;
    private Boolean rejectHandlerIsReturn = false;
    private Boolean processRunning = true;
}
