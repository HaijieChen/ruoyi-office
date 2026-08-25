package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.module.system.service.mfa.delivery.MfaChallengeDelivery;
import cn.iocoder.yudao.module.system.service.mfa.delivery.StubMfaChallengeDelivery;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaAuthFlowRecord;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaFactorBinding;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaFactorView;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaPendingEmail;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaPendingTotp;
import cn.iocoder.yudao.module.system.service.mfa.store.InMemoryMfaFactorStore;
import cn.iocoder.yudao.module.system.service.mfa.store.MfaFactorStore;
import cn.iocoder.yudao.module.system.service.mfa.support.MfaLockOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 因子通道（切片 4）：PENDING 直到签发成功；SMS/EMAIL 走可插拔投递。
 */
@Service
public class MfaFactorServiceImpl implements MfaFactorService {

    private static final Set<String> TYPES = Set.of("TOTP", "SMS", "EMAIL");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int OTP_TTL_SECONDS = 300;
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_REVOKED = "REVOKED";

    private final MfaAuthFlowService authFlowService;
    private final MfaFactorStore factorStore;
    private final MfaChallengeDelivery challengeDelivery;
    private final ConcurrentHashMap<String, DeliveryCode> deliveryCodes = new ConcurrentHashMap<>();

    public MfaFactorServiceImpl(MfaAuthFlowService authFlowService) {
        this(authFlowService, InMemoryMfaFactorStore.shared(), new StubMfaChallengeDelivery());
    }

    @Autowired
    public MfaFactorServiceImpl(MfaAuthFlowService authFlowService,
                                MfaFactorStore factorStore,
                                MfaChallengeDelivery challengeDelivery) {
        this.authFlowService = authFlowService;
        this.factorStore = factorStore == null ? InMemoryMfaFactorStore.shared() : factorStore;
        this.challengeDelivery = challengeDelivery == null ? new StubMfaChallengeDelivery() : challengeDelivery;
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
        MfaFactorBinding binding = requireBinding(flow.getTenantId(), flow.getUserId(), factorId, type, STATUS_ACTIVE);
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        String key = deliveryKey(MfaAuthFlowServiceImpl.sha256Hex(rawFlowToken), factorId);
        deliveryCodes.put(key, new DeliveryCode(code, Instant.now().plusSeconds(OTP_TTL_SECONDS), binding.getType()));
        challengeDelivery.deliver(type, binding.getSecretOrDestination(), code);
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
        MfaFactorBinding binding = requireBinding(flow.getTenantId(), flow.getUserId(), factorId, type, STATUS_ACTIVE);

        if ("TOTP".equals(type)) {
            Long step = matchTotpStep(binding, code.trim(), Instant.now());
            if (step == null) {
                return false;
            }
            return factorStore.claimTotpStep(flow.getTenantId(), flow.getUserId(), factorId, step);
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
        factorStore.save(tenantId, userId, MfaFactorBinding.builder()
                .factorId(factorId)
                .type(type)
                .status(STATUS_ACTIVE)
                .secretOrDestination(secretOrDestination)
                .label("身份验证器")
                .masked(null)
                .lastUsedStep(-1L)
                .build());
    }

    @Override
    public List<MfaFactorView> listActiveFactors(Long tenantId, Long userId) {
        List<MfaFactorView> out = new ArrayList<>();
        for (MfaFactorBinding b : factorStore.listByUser(tenantId, userId)) {
            if (STATUS_ACTIVE.equals(b.getStatus())) {
                out.add(MfaFactorView.builder()
                        .id(b.getFactorId())
                        .type(b.getType())
                        .status(b.getStatus())
                        .label(b.getLabel())
                        .maskedTarget(b.getMasked())
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
        MfaFactorBinding b = factorStore.get(tenantId, userId, factorId);
        return b == null ? null : b.getType();
    }

    @Override
    public MfaPendingTotp startPendingTotp(Long tenantId, Long userId, String accountName) {
        MfaLockOrder.requireValidOrder(List.of(MfaLockOrder.Resource.FACTOR));
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        byte[] secretBytes = new byte[20];
        RANDOM.nextBytes(secretBytes);
        String secret = cn.hutool.core.codec.Base32.encode(secretBytes).replace("=", "").toUpperCase(Locale.ROOT);
        String factorId = "totp-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String label = accountName == null ? ("user-" + userId) : accountName;
        String path = URLEncoder.encode("OA:" + label, StandardCharsets.UTF_8).replace("+", "%20");
        String otpauth = "otpauth://totp/" + path
                + "?secret=" + secret
                + "&issuer=OA&digits=6&period=30";
        factorStore.save(tenantId, userId, MfaFactorBinding.builder()
                .factorId(factorId)
                .type("TOTP")
                .status(STATUS_PENDING)
                .secretOrDestination(secret)
                .label("身份验证器")
                .masked(null)
                .lastUsedStep(-1L)
                .build());
        return MfaPendingTotp.builder()
                .factorId(factorId)
                .secretManual(secret)
                .otpauthUri(otpauth)
                .build();
    }

    @Override
    public Long matchPendingTotpStep(Long tenantId, Long userId, String factorId, String code) {
        MfaLockOrder.requireValidOrder(List.of(MfaLockOrder.Resource.FACTOR));
        MfaFactorBinding binding = factorStore.get(tenantId, userId, factorId);
        if (binding == null || !"TOTP".equals(binding.getType()) || !STATUS_PENDING.equals(binding.getStatus())) {
            return null;
        }
        return matchTotpStep(binding, code == null ? "" : code.trim(), Instant.now());
    }

    @Override
    public boolean tryActivatePendingFactor(Long tenantId, Long userId, String factorId, Long lastUsedStep) {
        MfaLockOrder.requireValidOrder(List.of(MfaLockOrder.Resource.FACTOR));
        return factorStore.casStatus(tenantId, userId, factorId, STATUS_PENDING, STATUS_ACTIVE, lastUsedStep);
    }

    @Override
    public boolean revertFactorToPending(Long tenantId, Long userId, String factorId) {
        return factorStore.casStatus(tenantId, userId, factorId, STATUS_ACTIVE, STATUS_PENDING, null);
    }

    @Override
    public boolean activatePendingTotp(Long tenantId, Long userId, String factorId, String code) {
        Long step = matchPendingTotpStep(tenantId, userId, factorId, code);
        if (step == null) {
            return false;
        }
        return tryActivatePendingFactor(tenantId, userId, factorId, step);
    }

    @Override
    public boolean hasActiveFactorOfType(Long tenantId, Long userId, String factorType) {
        String type = normalizeType(factorType);
        for (MfaFactorView view : listActiveFactors(tenantId, userId)) {
            if (type.equalsIgnoreCase(view.getType())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public MfaPendingEmail startPendingEmail(Long tenantId, Long userId, String email) {
        MfaLockOrder.requireValidOrder(List.of(MfaLockOrder.Resource.FACTOR));
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email blank");
        }
        String dest = email.trim();
        String factorId = "email-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String masked = maskEmail(dest);
        factorStore.save(tenantId, userId, MfaFactorBinding.builder()
                .factorId(factorId)
                .type("EMAIL")
                .status(STATUS_PENDING)
                .secretOrDestination(dest)
                .label("邮箱")
                .masked(masked)
                .lastUsedStep(-1L)
                .build());
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        deliveryCodes.put(enrollKey(tenantId, userId, factorId),
                new DeliveryCode(code, Instant.now().plusSeconds(OTP_TTL_SECONDS), "EMAIL"));
        challengeDelivery.deliver("EMAIL", dest, code);
        return MfaPendingEmail.builder().factorId(factorId).maskedEmail(masked).build();
    }

    @Override
    public boolean activatePendingEmail(Long tenantId, Long userId, String factorId, String code) {
        MfaLockOrder.requireValidOrder(List.of(MfaLockOrder.Resource.FACTOR));
        if (code == null || code.isBlank() || factorId == null) {
            return false;
        }
        String key = enrollKey(tenantId, userId, factorId);
        DeliveryCode dc = deliveryCodes.get(key);
        if (dc == null || Instant.now().isAfter(dc.expiresAt)) {
            if (dc != null) {
                deliveryCodes.remove(key);
            }
            return false;
        }
        if (!constantTimeEquals(dc.code, code.trim())) {
            return false;
        }
        deliveryCodes.remove(key);
        return tryActivatePendingFactor(tenantId, userId, factorId, null);
    }

    @Override
    public boolean revokeActiveFactor(Long tenantId, Long userId, String factorId) {
        MfaLockOrder.requireValidOrder(List.of(MfaLockOrder.Resource.FACTOR));
        return factorStore.casStatus(tenantId, userId, factorId, STATUS_ACTIVE, STATUS_REVOKED, null);
    }

    @Override
    public String peekFactorStatus(Long tenantId, Long userId, String factorId) {
        MfaFactorBinding b = factorStore.get(tenantId, userId, factorId);
        return b == null ? null : b.getStatus();
    }

    @Override
    public void clear() {
        factorStore.clear();
        deliveryCodes.clear();
        if (challengeDelivery instanceof StubMfaChallengeDelivery stub) {
            stub.clear();
        }
    }

    public String peekDeliveryCodeForTest(String rawFlowToken, String factorId) {
        DeliveryCode dc = deliveryCodes.get(deliveryKey(MfaAuthFlowServiceImpl.sha256Hex(rawFlowToken), factorId));
        return dc == null ? null : dc.code;
    }

    public MfaChallengeDelivery challengeDelivery() {
        return challengeDelivery;
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

    private MfaFactorBinding requireBinding(Long tenantId, Long userId, String factorId, String type, String status) {
        MfaFactorBinding b = factorStore.get(tenantId, userId, factorId);
        if (b == null || !b.getType().equals(type) || !status.equals(b.getStatus())) {
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

    private static String deliveryKey(String flowHash, String factorId) {
        return flowHash + ":" + factorId;
    }

    private static String enrollKey(Long tenantId, Long userId, String factorId) {
        return "enroll:" + tenantId + ":" + userId + ":" + factorId;
    }

    static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        String keep = local.substring(0, Math.min(2, local.length()));
        return keep + "***" + domain;
    }

    private static Long matchTotpStep(MfaFactorBinding binding, String code, Instant now) {
        long step = now.getEpochSecond() / 30L;
        for (long s = step - 1; s <= step + 1; s++) {
            if (constantTimeEquals(hotp(binding.getSecretOrDestination(), s), code)) {
                return s;
            }
        }
        return null;
    }

    private static String hotp(String secret, long counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decodeTotpKey(secret), "HmacSHA1"));
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

    private static byte[] decodeTotpKey(String secret) {
        if (secret == null) {
            return new byte[0];
        }
        String compact = secret.trim().replace(" ", "").replace("=", "").toUpperCase(Locale.ROOT);
        if (compact.length() >= 16 && compact.matches("[A-Z2-7]+")) {
            return cn.hutool.core.codec.Base32.decode(compact);
        }
        return secret.getBytes(StandardCharsets.UTF_8);
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

    private record DeliveryCode(String code, Instant expiresAt, String type) {
    }
}
