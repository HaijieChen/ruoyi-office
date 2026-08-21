package cn.iocoder.yudao.module.bpm.controller.admin.task;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.share.BpmProcessInstanceShareReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.share.BpmProcessInstanceShareRespVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.share.BpmProcessInstanceShareRevokeReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.task.BpmProcessInstanceShareDO;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 流程实例分享")
@RestController
@RequestMapping("/bpm/process-instance/share")
@Validated
public class BpmProcessInstanceShareController {

    @Resource
    private BpmProcessInstanceShareService shareService;

    @PostMapping("/create")
    @Operation(summary = "分享已通过的流程实例")
    @PreAuthorize("@ss.hasPermission('bpm:process-instance:query')")
    public CommonResult<Boolean> share(@Valid @RequestBody BpmProcessInstanceShareReqVO reqVO) {
        shareService.share(getLoginUserId(), reqVO.getProcessInstanceId(), reqVO.getRecipientUserIds());
        return success(true);
    }

    @PostMapping("/revoke")
    @Operation(summary = "收回流程实例分享")
    @PreAuthorize("@ss.hasPermission('bpm:process-instance:query')")
    public CommonResult<Boolean> revoke(@Valid @RequestBody BpmProcessInstanceShareRevokeReqVO reqVO) {
        shareService.revoke(getLoginUserId(), reqVO.getProcessInstanceId(), reqVO.getRecipientUserId());
        return success(true);
    }

    @GetMapping("/recipients")
    @Operation(summary = "发起人查看当前有效接收人")
    @Parameter(name = "processInstanceId", required = true)
    @PreAuthorize("@ss.hasPermission('bpm:process-instance:query')")
    public CommonResult<List<BpmProcessInstanceShareRespVO>> getRecipients(
            @RequestParam("processInstanceId") String processInstanceId) {
        List<BpmProcessInstanceShareDO> list = shareService.getActiveRecipients(getLoginUserId(), processInstanceId);
        return success(BeanUtils.toBean(list, BpmProcessInstanceShareRespVO.class));
    }

    @GetMapping("/my-page")
    @Operation(summary = "分享给我的分页")
    @PreAuthorize("@ss.hasPermission('bpm:process-instance:query')")
    public CommonResult<PageResult<BpmProcessInstanceShareRespVO>> getMyPage(@Valid PageParam pageParam) {
        PageResult<BpmProcessInstanceShareDO> page = shareService.getSharedWithMePage(getLoginUserId(), pageParam);
        return success(BeanUtils.toBean(page, BpmProcessInstanceShareRespVO.class));
    }
}
