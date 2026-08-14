package cn.iocoder.yudao.module.system.service.mfa.store;

import cn.iocoder.yudao.module.system.service.mfa.model.MfaFactorBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 同 JVM 共享因子 store。
 */
public class InMemoryMfaFactorStore implements MfaFactorStore {

    private static final InMemoryMfaFactorStore SHARED = new InMemoryMfaFactorStore();

    private final ConcurrentHashMap<String, MfaFactorBinding> map = new ConcurrentHashMap<>();

    public static InMemoryMfaFactorStore shared() {
        return SHARED;
    }

    @Override
    public void save(Long tenantId, Long userId, MfaFactorBinding binding) {
        map.put(key(tenantId, userId, binding.getFactorId()), binding);
    }

    @Override
    public MfaFactorBinding get(Long tenantId, Long userId, String factorId) {
        return map.get(key(tenantId, userId, factorId));
    }

    @Override
    public List<MfaFactorBinding> listByUser(Long tenantId, Long userId) {
        String prefix = tenantId + ":" + userId + ":";
        List<MfaFactorBinding> out = new ArrayList<>();
        for (Map.Entry<String, MfaFactorBinding> e : map.entrySet()) {
            if (e.getKey().startsWith(prefix)) {
                out.add(e.getValue());
            }
        }
        return out;
    }

    @Override
    public boolean casStatus(Long tenantId, Long userId, String factorId,
                             String fromStatus, String toStatus, Long lastUsedStep) {
        String k = key(tenantId, userId, factorId);
        while (true) {
            MfaFactorBinding cur = map.get(k);
            if (cur == null || !fromStatus.equals(cur.getStatus())) {
                return false;
            }
            MfaFactorBinding next = cur.toBuilder()
                    .status(toStatus)
                    .lastUsedStep(lastUsedStep == null ? cur.getLastUsedStep() : lastUsedStep)
                    .build();
            if (map.replace(k, cur, next)) {
                return true;
            }
        }
    }

    @Override
    public boolean claimTotpStep(Long tenantId, Long userId, String factorId, long step) {
        String k = key(tenantId, userId, factorId);
        while (true) {
            MfaFactorBinding cur = map.get(k);
            if (cur == null) {
                return false;
            }
            if (step <= cur.getLastUsedStep()) {
                return false;
            }
            MfaFactorBinding next = cur.toBuilder().lastUsedStep(step).build();
            if (map.replace(k, cur, next)) {
                return true;
            }
        }
    }

    @Override
    public void clear() {
        map.clear();
    }

    private static String key(Long tenantId, Long userId, String factorId) {
        return tenantId + ":" + userId + ":" + factorId;
    }
}
