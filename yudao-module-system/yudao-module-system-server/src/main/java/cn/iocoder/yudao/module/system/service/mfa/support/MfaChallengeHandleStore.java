package cn.iocoder.yudao.module.system.service.mfa.support;

import cn.iocoder.yudao.module.system.service.mfa.enums.MfaTokenClass;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 预认证/绑定挑战句柄内存骨架（③ 将迁移 Redis）。
 * <p>
 * 句柄本身带 {@link MfaTokenClass}，不得当作业务 Bearer。
 */
public class MfaChallengeHandleStore {

    private static final SecureRandom RANDOM = new SecureRandom();

    public record ChallengeHandle(
            String token,
            MfaTokenClass tokenClass,
            Long userId,
            Long tenantId,
            long policyVersion,
            Instant expiresAt
    ) {
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    private final Map<String, ChallengeHandle> handles = new ConcurrentHashMap<>();

    public ChallengeHandle create(MfaTokenClass tokenClass, Long userId, Long tenantId,
                                  long policyVersion, int ttlSeconds) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        ChallengeHandle handle = new ChallengeHandle(
                token, tokenClass, userId, tenantId, policyVersion,
                Instant.now().plusSeconds(ttlSeconds));
        handles.put(token, handle);
        return handle;
    }

    public ChallengeHandle get(String token) {
        ChallengeHandle handle = handles.get(token);
        if (handle == null || handle.isExpired()) {
            if (handle != null) {
                handles.remove(token);
            }
            return null;
        }
        return handle;
    }

    public void remove(String token) {
        handles.remove(token);
    }

    public void clear() {
        handles.clear();
    }

}
