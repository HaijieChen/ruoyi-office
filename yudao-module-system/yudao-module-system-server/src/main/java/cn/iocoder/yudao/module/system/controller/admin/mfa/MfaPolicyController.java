package cn.iocoder.yudao.module.system.controller.admin.mfa;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.system.controller.admin.mfa.vo.MfaPolicyRespVO;
import cn.iocoder.yudao.module.system.controller.admin.mfa.vo.MfaPolicySaveReqVO;
import cn.iocoder.yudao.module.system.service.mfa.MfaPolicyControlService;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaPolicySnapshot;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - MFA 策略")
@RestController
@RequestMapping("/system/mfa-policy")
@Validated
public class MfaPolicyController {

    @Resource
    private MfaPolicyControlService mfaPolicyControlService;

    @GetMapping("/get")
    @Operation(summary = "获得全局 MFA 策略")
    @PreAuthorize("@ss.hasPermission('system:mfa-policy:query')")
    public CommonResult<MfaPolicyRespVO> getPolicy() {
        MfaPolicySnapshot snapshot = mfaPolicyControlService.resolveEffectivePolicy(null);
        MfaPolicyRespVO vo = new MfaPolicyRespVO();
        vo.setLifecycleState(snapshot.getLifecycleState() == null ? null : snapshot.getLifecycleState().name());
        vo.setMode(snapshot.getMode() == null ? null : snapshot.getMode().name());
        vo.setAllowedFactors(snapshot.getAllowedFactors() == null
                ? new ArrayList<>() : new ArrayList<>(snapshot.getAllowedFactors()));
        vo.setUsable(snapshot.isUsable());
        vo.setUnusableReason(snapshot.getUnusableReason());
        vo.setGlobalPolicyEpoch(snapshot.getGlobalPolicyEpoch());
        vo.setGlobalMinAcceptedEpoch(snapshot.getGlobalMinAcceptedEpoch());
        return success(vo);
    }

    @PutMapping("/update")
    @Operation(summary = "更新全局 MFA 策略")
    @PreAuthorize("@ss.hasPermission('system:mfa-policy:update')")
    public CommonResult<Long> updatePolicy(@Valid @RequestBody MfaPolicySaveReqVO reqVO) {
        long epoch = mfaPolicyControlService.confirmGlobalPolicy(reqVO.getMode(), reqVO.getAllowedFactors());
        mfaPolicyControlService.invalidateCache();
        return success(epoch);
    }
}
