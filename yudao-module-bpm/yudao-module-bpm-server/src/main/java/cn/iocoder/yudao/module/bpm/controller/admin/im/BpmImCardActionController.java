package cn.iocoder.yudao.module.bpm.controller.admin.im;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.bpm.framework.im.ImCardActionResult;
import cn.iocoder.yudao.module.bpm.framework.im.ImCardActionService;
import cn.iocoder.yudao.module.bpm.framework.im.ImCardTaskSnapshot;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - IM 卡片动作")
@RestController
@RequestMapping("/bpm/im/card")
@Validated
public class BpmImCardActionController {

    @Resource
    private ImCardActionService imCardActionService;

    @PostMapping("/action")
    @PermitAll
    @Operation(summary = "IM 卡片通过/拒绝/撤回（无 OA cookie，调用方须已解析 IM 用户）")
    public CommonResult<ImCardActionResult> action(@Valid @RequestBody ImCardActionReqVO reqVO) {
        return success(imCardActionService.handle(reqVO.getActorUserId(), reqVO.getAction(),
                reqVO.getSnapshot(), reqVO.getEventId()));
    }

    @Data
    public static class ImCardActionReqVO {
        @NotNull
        private Long actorUserId;
        @NotEmpty
        private String action;
        @NotEmpty
        private String eventId;
        @NotNull
        private ImCardTaskSnapshot snapshot;
    }
}
