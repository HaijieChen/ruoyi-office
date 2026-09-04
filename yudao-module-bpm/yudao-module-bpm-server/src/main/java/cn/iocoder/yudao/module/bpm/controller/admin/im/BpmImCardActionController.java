package cn.iocoder.yudao.module.bpm.controller.admin.im;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.bpm.framework.im.ImCardActionResult;
import cn.iocoder.yudao.module.bpm.framework.im.ImCardSignedActionService;
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
    private ImCardSignedActionService imCardSignedActionService;

    @PostMapping("/action")
    @PermitAll
    @Operation(summary = "IM 卡片通过/拒绝/撤回。验签后按 openid 映射 OA 用户，不信任 actorUserId")
    public CommonResult<ImCardActionResult> action(@Valid @RequestBody ImCardActionReqVO reqVO) {
        return success(imCardSignedActionService.handle(reqVO.getSocialType(), reqVO.getOpenid(),
                reqVO.getTimestamp(), reqVO.getSignature(), reqVO.getAction(), reqVO.getEventId(),
                reqVO.getSnapshot()));
    }

    @Data
    public static class ImCardActionReqVO {
        @NotNull
        private Integer socialType;
        @NotEmpty
        private String openid;
        @NotNull
        private Long timestamp;
        @NotEmpty
        private String signature;
        @NotEmpty
        private String action;
        @NotEmpty
        private String eventId;
        @NotNull
        private ImCardTaskSnapshot snapshot;
    }
}
