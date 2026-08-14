package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.module.system.service.mfa.model.MfaAuthFlowRecord;
import cn.iocoder.yudao.module.system.service.mfa.support.MfaLockOrder;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 切片 2 最小因子通道：SMS/EMAIL 投递码内存存储 + RFC6238 TOTP 校验（测试/开发用）。
 * 生产投递应走 Delivery Adapter；本实现不决定身份授权。
 */
@Service
public class MfaFactorServiceImpl implements MfaFactorService {

    private static final Set<String> TYPES = Set.of("TOTP", "SMS", "EMAIL");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int OTP_TTL_SECONDS = 300;

    private final MfaAuthFlowService authFlowService;

    /** factorKey tenant:user:factorId -> FactorBinding */
    private final Map<String, FactorBinding> factors = new ConcurrentHashMap<>();
    /** deliveryKey flowHash:factorId -> DeliveryCode */
    private final Map<String, DeliveryCode> deliveryCodes = new ConcurrentHashMap<>();

    public MfaFactorServiceImpl(MfaAuthFlowService authFlowService) {
        this.authFlowService = authFlowService;
    }

    @Override
    public void sendChallengeCode(String rawFlowToken, String factorId, String factorType) {
        MfaLockOrder.requireValidOrder(java.util.List.of(
                MfaLockOrder.Resource.FACTOR,
                MfaLockOrder.Resource.FLOW_OR_DECISION_OR_CODE));
        String type = normalizeType(factorType);
        if ("TOTP".equals(type)) {
            throw new IllegalArgumentException("TOTP does not use sendChallengeCode");
        }
        MfaAuthFlowRecord flow = requireActiveFlow(rawFlowToken);
        FactorBinding binding = requireBinding(flow.getTenantId(), flow.getUserId(), factorId, type);
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        String key = deliveryKey(MfaAuthFlowServiceImpl.sha256Hex(rawFlowToken), factorId);
        deliveryCodes.put(key, new DeliveryCode(code, Instant.now().plusSeconds(OTP_TTL_SECONDS), binding.type));
        // 生产：调用 SMS/EMAIL adapter 发送 code；此处仅存储供校验
    }

    @Override
    public boolean verifyChallengeCode(String rawFlowToken, String factorId, String factorType, String code) {
        MfaLockOrder.requireValidOrder(java.util.List.of(
                MfaLockOrder.Resource.FACTOR,
                MfaLockOrder.Resource.FLOW_OR_DECISION_OR_CODE));
        if (code == null || code.isBlank()) {
            return false;
        }
        String type = normalizeType(factorType);
        MfaAuthFlowRecord flow = requireActiveFlow(rawFlowToken);
        FactorBinding binding = requireBinding(flow.getTenantId(), flow.getUserId(), factorId, type);

        if ("TOTP".equals(type)) {
            return verifyTotp(binding.secretOrDestination, code.trim(), Instant.now());
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
            deliveryCodes.remove(key); // 单次消费投递码
        }
        return ok;
    }

    @Override
    public void registerActiveFactor(Long tenantId, Long userId, String factorId, String factorType,
                                     String secretOrDestination) {
        String type = normalizeType(factorType);
        factors.put(factorKey(tenantId, userId, factorId),
                new FactorBinding(factorId, type, secretOrDestination));
    }

    @Override
    public void clear() {
        factors.clear();
        deliveryCodes.clear();
    }

    /** 测试可见：读取最近一次发送的码（生产禁止暴露）。 */
    public String peekDeliveryCodeForTest(String rawFlowToken, String factorId) {
        DeliveryCode dc = deliveryCodes.get(deliveryKey(MfaAuthFlowServiceImpl.sha256Hex(rawFlowToken), factorId));
        return dc == null ? null : dc.code;
    }

    private MfaAuthFlowRecord requireActiveFlow(String rawFlowToken) {
        MfaAuthFlowRecord flow = authFlowService.resolveActive(rawFlowToken);
        if (flow == null) {
            throw new IllegalStateException("flow not active");
        }
        return flow;
    }

    private FactorBinding requireBinding(Long tenantId, Long userId, String factorId, String type) {
        FactorBinding b = factors.get(factorKey(tenantId, userId, factorId));
        if (b == null || !b.type.equals(type)) {
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

    private static boolean verifyTotp(String base32OrAsciiSecret, String code, Instant now) {
        // 简化：以 UTF-8 secret 作为 HMAC key（测试用）；窗口 ±1
        long step = now.getEpochSecond() / 30L;
        for (long s = step - 1; s <= step + 1; s++) {
            if (constantTimeEquals(hotp(base32OrAsciiSecret, s), code)) {
                return true;
            }
        }
        return false;
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
            int otp = binary % 1_000_000;
            return String.format("%06d", otp);
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

    private record FactorBinding(String factorId, String type, String secretOrDestination) {
    }

    private record DeliveryCode(String code, Instant expiresAt, String type) {
    }
}
