package cn.iocoder.yudao.module.system.controller.admin.mfa;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthMfaEnrollmentTotpStartRespVO;
import cn.iocoder.yudao.module.system.controller.admin.mfa.vo.MfaUserEmailStartRespVO;
import cn.iocoder.yudao.module.system.controller.admin.mfa.vo.MfaUserFactorRespVO;
import cn.iocoder.yudao.module.system.controller.admin.mfa.vo.MfaUserTotpConfirmReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.service.mfa.MfaAssuranceAuthority;
import cn.iocoder.yudao.module.system.service.mfa.MfaFactorService;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaEnrollmentState;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaFactorView;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaPendingEmail;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaPendingTotp;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.MFA_EMAIL_ALREADY_BOUND;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.MFA_EMAIL_NOT_EXISTS;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.MFA_FACTOR_NOT_FOUND;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.MFA_FACTOR_VERIFY_FAILED;

@Tag(name = "管理后台 - 个人 MFA")
@RestController
@RequestMapping("/system/mfa/factor")
@Validated
public class MfaUserFactorController {

    @Resource
    private MfaFactorService mfaFactorService;
    @Resource
    private MfaAssuranceAuthority mfaAssuranceAuthority;
    @Resource
    private AdminUserService adminUserService;

    @GetMapping("/list")
    @Operation(summary = "我的 MFA 设备")
    public CommonResult<List<MfaUserFactorRespVO>> listMyFactors() {
        Long userId = getLoginUserId();
        Long tenantId = TenantContextHolder.getTenantId();
        List<MfaUserFactorRespVO> out = new ArrayList<>();
        for (MfaFactorView view : mfaFactorService.listActiveFactors(tenantId, userId)) {
            MfaUserFactorRespVO vo = new MfaUserFactorRespVO();
            vo.setId(view.getId());
            vo.setType(view.getType());
            vo.setStatus(view.getStatus());
            vo.setLabel(view.getLabel());
            vo.setMaskedTarget(view.getMaskedTarget());
            out.add(vo);
        }
        return success(out);
    }

    @PostMapping("/totp/start")
    @Operation(summary = "开始绑定 TOTP")
    public CommonResult<AuthMfaEnrollmentTotpStartRespVO> startTotp() {
        Long userId = getLoginUserId();
        Long tenantId = TenantContextHolder.getTenantId();
        AdminUserDO user = adminUserService.getUser(userId);
        String account = user == null || user.getUsername() == null ? String.valueOf(userId) : user.getUsername();
        MfaPendingTotp pending = mfaFactorService.startPendingTotp(tenantId, userId, account);
        return success(AuthMfaEnrollmentTotpStartRespVO.builder()
                .factorId(pending.getFactorId())
                .secretManual(pending.getSecretManual())
                .otpauthUri(pending.getOtpauthUri())
                .build());
    }

    @PostMapping("/totp/confirm")
    @Operation(summary = "确认绑定 TOTP")
    public CommonResult<Boolean> confirmTotp(@Valid @RequestBody MfaUserTotpConfirmReqVO reqVO) {
        Long userId = getLoginUserId();
        Long tenantId = TenantContextHolder.getTenantId();
        boolean ok = mfaFactorService.activatePendingTotp(tenantId, userId, reqVO.getFactorId(), reqVO.getCode());
        if (!ok) {
            throw exception(MFA_FACTOR_VERIFY_FAILED);
        }
        mfaAssuranceAuthority.ensureBootstrapRow(tenantId, userId);
        mfaAssuranceAuthority.bumpAssuranceEpoch(tenantId, userId, MfaEnrollmentState.COMPLETED, Boolean.TRUE);
        return success(true);
    }

    @PostMapping("/email/start")
    @Operation(summary = "开始绑定邮箱 MFA")
    public CommonResult<MfaUserEmailStartRespVO> startEmail() {
        Long userId = getLoginUserId();
        Long tenantId = TenantContextHolder.getTenantId();
        if (mfaFactorService.hasActiveFactorOfType(tenantId, userId, "EMAIL")) {
            throw exception(MFA_EMAIL_ALREADY_BOUND);
        }
        AdminUserDO user = adminUserService.getUser(userId);
        String email = user == null ? null : user.getEmail();
        if (email == null || email.isBlank()) {
            throw exception(MFA_EMAIL_NOT_EXISTS);
        }
        MfaPendingEmail pending = mfaFactorService.startPendingEmail(tenantId, userId, email);
        MfaUserEmailStartRespVO vo = new MfaUserEmailStartRespVO();
        vo.setFactorId(pending.getFactorId());
        vo.setMaskedEmail(pending.getMaskedEmail());
        return success(vo);
    }

    @PostMapping("/email/confirm")
    @Operation(summary = "确认绑定邮箱 MFA")
    public CommonResult<Boolean> confirmEmail(@Valid @RequestBody MfaUserTotpConfirmReqVO reqVO) {
        Long userId = getLoginUserId();
        Long tenantId = TenantContextHolder.getTenantId();
        boolean ok = mfaFactorService.activatePendingEmail(tenantId, userId, reqVO.getFactorId(), reqVO.getCode());
        if (!ok) {
            throw exception(MFA_FACTOR_VERIFY_FAILED);
        }
        mfaAssuranceAuthority.ensureBootstrapRow(tenantId, userId);
        mfaAssuranceAuthority.bumpAssuranceEpoch(tenantId, userId, MfaEnrollmentState.COMPLETED, Boolean.TRUE);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "解绑 MFA 设备")
    @Parameter(name = "factorId", description = "因子 ID", required = true)
    public CommonResult<Boolean> deleteFactor(@RequestParam("factorId") String factorId) {
        Long userId = getLoginUserId();
        Long tenantId = TenantContextHolder.getTenantId();
        boolean ok = mfaFactorService.revokeActiveFactor(tenantId, userId, factorId);
        if (!ok) {
            throw exception(MFA_FACTOR_NOT_FOUND);
        }
        return success(true);
    }
}
