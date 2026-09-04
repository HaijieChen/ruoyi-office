package cn.iocoder.yudao.module.bpm.framework.im;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ImCardActionResult {

    public static final String DONE = "DONE";
    public static final String OPEN_APP = "OPEN_APP";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String DUPLICATE = "DUPLICATE";

    private String outcome;
    private String message;

    public static ImCardActionResult done() {
        return new ImCardActionResult(DONE, "已办理");
    }

    public static ImCardActionResult openApp() {
        return new ImCardActionResult(OPEN_APP, "请打开应用办理");
    }

    public static ImCardActionResult forbidden(String message) {
        return new ImCardActionResult(FORBIDDEN, message);
    }

    public static ImCardActionResult duplicate() {
        return new ImCardActionResult(DUPLICATE, "该待办已处理");
    }
}
