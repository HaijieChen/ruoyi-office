package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.iocoder.yudao.module.system.dal.mysql.oauth2.OAuth2AccessTokenMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 切片 3：基于因子权威探测用户 MFA 就绪状态。
 */
@Service
public class MfaUserFactorProbeImpl implements MfaUserFactorProbe {

    private final MfaFactorService factorService;
    private final OAuth2AccessTokenMapper accessTokenMapper;

    public MfaUserFactorProbeImpl(MfaFactorService factorService) {
        this(factorService, null);
    }

    @Autowired
    public MfaUserFactorProbeImpl(MfaFactorService factorService,
                                  OAuth2AccessTokenMapper accessTokenMapper) {
        this.factorService = factorService;
        this.accessTokenMapper = accessTokenMapper;
    }

    @Override
    public boolean isUserMfaEnabled(Long tenantId, Long userId) {
        return hasEligibleActiveFactor(tenantId, userId);
    }

    @Override
    public boolean hasEligibleActiveFactor(Long tenantId, Long userId) {
        return factorService.hasActiveFactor(tenantId, userId);
    }

    @Override
    public boolean isEnrollmentComplete(Long tenantId, Long userId) {
        return factorService.hasActiveFactor(tenantId, userId);
    }

    @Override
    public Long getSessionPolicyVersion(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank() || accessTokenMapper == null) {
            return null;
        }
        List<OAuth2AccessTokenDO> tokens = accessTokenMapper.selectListByRefreshToken(refreshToken);
        if (tokens == null || tokens.isEmpty()) {
            return null;
        }
        Map<String, String> info = tokens.get(0).getUserInfo();
        if (info == null) {
            return null;
        }
        String raw = info.get(MfaSessionGuardImpl.UI_GLOBAL_EPOCH);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Override
    public boolean isEnrollmentPendingOnSession(String refreshToken) {
        return false;
    }

    @Override
    public boolean isRecoveryPendingOnSession(String refreshToken) {
        return false;
    }
}
