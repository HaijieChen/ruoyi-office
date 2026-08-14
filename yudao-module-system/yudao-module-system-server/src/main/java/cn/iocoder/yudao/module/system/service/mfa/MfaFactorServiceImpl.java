package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.module.system.service.mfa.model.MfaAuthFlowRecord;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaFactorView;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaPendingTotp;
import cn.iocoder.yudao.module.system.service.mfa.support.MfaLockOrder;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 因子通道实现（切片 3）：allowlist、TOTP last_used_step、PENDING→ACTIVE 绑定。
 * 存储为进程内权威可切换边界（生产可换 DB）。
 */
@Service
public class MfaFactorServiceImpl implements MfaFactorService {

    private static final Set<String> TYPES = Set.of("TOTP", "SMS", "EMAIL");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int OTP_TTL_SECONDS = 300;
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_PENDING = "PENDING";

    private final MfaAuthFlowService authFlowService;
    private final Map<String, FactorBinding> factors = new ConcurrentHashMap<>();
    private final Map<String, DeliveryCode> deliveryCodes = new ConcurrentHashMap<>();

    public MfaFactorServiceImpl(MfaAuthFlowService authFlowService) {
        this.authFlowService = authFlowService;
    }

    @Override
    public void sendChallengeCode(String rawFlowToken, String factorId, String factorType) {
        MfaLockOrder.requireValidOrder(List.of(
                MfaLockOrder.Resource.FACTOR,
                MfaLockOrder.Resource.FLOW_OR_DECISION_OR_CODE));
        String type = normalizeType(factorType);
        if ("TOTP".equals(type)) {
            throw new IllegalArgumentException("TOTP does not use sendChallengeCode");
        }
        MfaAuthFlowRecord flow = requireActiveFlow(rawFlowToken);
        requireActionAllowed(flow, "send");
        requireFactorAllowed(flow, factorId);
        FactorBinding binding = requireBinding(flow.getTenantId(), flow.getUserId(), factorId, type, STATUS_ACTIVE);
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        String key = deliveryKey(MfaAuthFlowServiceImpl.sha256Hex(rawFlowToken), factorId);
        deliveryCodes.put(key, new DeliveryCode(code, Instant.now().plusSeconds(OTP_TTL_SECONDS), binding.type));
    }

    @Override
    public boolean verifyChallengeCode(String rawFlowToken, String factorId, String factorType, String code) {
        MfaLockOrder.requireValidOrder(List.of(
                MfaLockOrder.Resource.FACTOR,
                MfaLockOrder.Resource.FLOW_OR_DECISION_OR_CODE));
        if (code == null || code.isBlank()) {
            return false;
        }
        String type = normalizeType(factorType);
        MfaAuthFlowRecord flow = requireActiveFlow(rawFlowToken);
        requireActionAllowed(flow, "verify");
        requireFactorAllowed(flow, factorId);
        FactorBinding binding = requireBinding(flow.getTenantId(), flow.getUserId(), factorId, type, STATUS_ACTIVE);

        if ("TOTP".equals(type)) {
            return verifyTotpWithReplayGuard(binding, code.trim(), Instant.now());
        }
        String key = deliveryKey(MfaAuthFlowServiceImpl.sha256Hex(rawFlowToken), factorId);
        DeliveryCode dc = deliveryCodes.get(key);
        if (dc == null || Instant.now().isAfter(dc.expiresAt)) {
            if (dc != null) {
                deliveryCodes.remove(key);
            }
            return false;
        }
        boolean ok = constantTimeEquals(dc.code, code.trim());
        if (ok) {
            deliveryCodes.remove(key);
        }
        return ok;
    }

    @Override
    public void registerActiveFactor(Long tenantId, Long userId, String factorId, String factorType,
                                     String secretOrDestination) {
        String type = normalizeType(factorType);
        factors.put(factorKey(tenantId, userId, factorId),
                new FactorBinding(factorId, type, STATUS_ACTIVE, secretOrDestination, "Authenticator",
                        null, new AtomicLong(-1L)));
    }

    @Override
    public List<MfaFactorView> listActiveFactors(Long tenantId, Long userId) {
        List<MfaFactorView> out = new ArrayList<>();
        String prefix = tenantId + ":" + userId + ":";
        for (Map.Entry<String, FactorBinding> e : factors.entrySet()) {
            if (e.getKey().startsWith(prefix) && STATUS_ACTIVE.equals(e.getValue().status)) {
                FactorBinding b = e.getValue();
                out.add(MfaFactorView.builder()
                        .id(b.factorId)
                        .type(b.type)
                        .status(b.status)
                        .label(b.label)
                        .maskedTarget(b.masked)
                        .build());
            }
        }
        return out;
    }

    @Override
    public boolean hasActiveFactor(Long tenantId, Long userId) {
        return !listActiveFactors(tenantId, userId).isEmpty();
    }

    @Override
    public String resolveFactorType(Long tenantId, Long userId, String factorId) {
        FactorBinding b = factors.get(factorKey(tenantId, userId, factorId));
        return b == null ? null : b.type;
    }

    @Override
    public MfaPendingTotp startPendingTotp(Long tenantId, Long userId, String accountName) {
        MfaLockOrder.requireValidOrder(List.of(MfaLockOrder.Resource.FACTOR));
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        byte[] secretBytes = new byte[20];
        RANDOM.nextBytes(secretBytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
        String factorId = "totp-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String label = accountName == null ? ("user-" + userId) : accountName;
        String otpauth = "otpauth://totp/OA:" + label
                + "?secret=" + secret
                + "&issuer=OA&algorithm=SHA1&digits=6&period=30";
        factors.put(factorKey(tenantId, userId, factorId),
                new FactorBinding(factorId, "TOTP", STATUS_PENDING, secret, "Authenticator",
                        null, new AtomicLong(-1L)));
        return MfaPendingTotp.builder()
                .factorId(factorId)
                .secretManual(secret)
                .otpauthUri(otpauth)
                .build();
    }

    @Override
    public boolean activatePendingTotp(Long tenantId, Long userId, String factorId, String code) {
        MfaLockOrder.requireValidOrder(List.of(MfaLockOrder.Resource.FACTOR));
        FactorBinding binding = factors.get(factorKey(tenantId, userId, factorId));
        if (binding == null || !"TOTP".equals(binding.type) || !STATUS_PENDING.equals(binding.status)) {
            return false;
        }
        if (!verifyTotpWithReplayGuard(binding, code == null ? "" : code.trim(), Instant.now())) {
            return false;
        }
        factors.put(factorKey(tenantId, userId, factorId),
                new FactorBinding(binding.factorId, binding.type, STATUS_ACTIVE, binding.secretOrDestination,
                        binding.label, binding.masked, binding.lastUsedStep));
        return true;
    }

    @Override
    public void clear() {
        factors.clear();
        deliveryCodes.clear();
    }

    public String peekDeliveryCodeForTest(String rawFlowToken, String factorId) {
        DeliveryCode dc = deliveryCodes.get(deliveryKey(MfaAuthFlowServiceImpl.sha256Hex(rawFlowToken), factorId));
        return dc == null ? null : dc.code;
    }

    private static void requireActionAllowed(MfaAuthFlowRecord flow, String action) {
        if (flow.getAllowedActions() == null || flow.getAllowedActions().isEmpty()) {
            return;
        }
        if (!flow.getAllowedActions().contains(action)) {
            throw new IllegalStateException("action not allowed on flow: " + action);
        }
    }

    private static void requireFactorAllowed(MfaAuthFlowRecord flow, String factorId) {
        if (flow.getAllowedFactorIds() == null || flow.getAllowedFactorIds().isEmpty()) {
            return;
        }
        if (!flow.getAllowedFactorIds().contains(factorId)) {
            throw new IllegalStateException("factor not allowed on flow: " + factorId);
        }
    }

    private MfaAuthFlowRecord requireActiveFlow(String rawFlowToken) {
        MfaAuthFlowRecord flow = authFlowService.resolveActive(rawFlowToken);
        if (flow == null) {
            throw new IllegalStateException("flow not active");
        }
        return flow;
    }

    private FactorBinding requireBinding(Long tenantId, Long userId, String factorId, String type, String status) {
        FactorBinding b = factors.get(factorKey(tenantId, userId, factorId));
        if (b == null || !b.type.equals(type) || !status.equals(b.status)) {
            throw new IllegalStateException("factor not registered: " + factorId);
        }
        return b;
    }

    private static String normalizeType(String factorType) {
        Objects.requireNonNull(factorType, "factorType");
        String t = factorType.trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(t)) {
            throw new IllegalArgumentException("unknown factor type: " + factorType);
        }
        return t;
    }

    private static String factorKey(Long tenantId, Long userId, String factorId) {
        return tenantId + ":" + userId + ":" + factorId;
    }

    private static String deliveryKey(String flowHash, String factorId) {
        return flowHash + ":" + factorId;
    }

    private static boolean verifyTotpWithReplayGuard(FactorBinding binding, String code, Instant now) {
        long step = now.getEpochSecond() / 30L;
        Long matchedStep = null;
        for (long s = step - 1; s <= step + 1; s++) {
            if (constantTimeEquals(hotp(binding.secretOrDestination, s), code)) {
                matchedStep = s;
                break;
            }
        }
        if (matchedStep == null) {
            return false;
        }
        while (true) {
            long prev = binding.lastUsedStep.get();
            if (matchedStep <= prev) {
                return false;
            }
            if (binding.lastUsedStep.compareAndSet(prev, matchedStep)) {
                return true;
            }
        }
    }

    private static String hotp(String secret, long counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            return String.format("%06d", binary % 1_000_000);
        } catch (Exception e) {
            throw new IllegalStateException("TOTP failed", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < a.length(); i++) {
            r |= a.charAt(i) ^ b.charAt(i);
        }
        return r == 0;
    }

    private record FactorBinding(String factorId, String type, String status, String secretOrDestination,
                                 String label, String masked, AtomicLong lastUsedStep) {
    }

    private record DeliveryCode(String code, Instant expiresAt, String type) {
    }
}
