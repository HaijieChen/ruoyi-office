package cn.iocoder.yudao.module.system.service.mfa.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 策略 checksum：mode + allowedFactors + epoch。
 */
public final class MfaChecksumUtil {

    private MfaChecksumUtil() {
    }

    public static String compute(String mode, Set<String> allowedFactors, long epoch) {
        String factors = allowedFactors == null ? "" :
                allowedFactors.stream().sorted().collect(Collectors.joining(","));
        String material = (mode == null ? "null" : mode) + "|" + factors + "|" + epoch;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static String computeControl(String lifecycle, String mode, Set<String> factors,
                                        long policyEpoch, long minAcceptedEpoch) {
        String material = lifecycle + "|" + mode + "|" +
                (factors == null ? "" : factors.stream().sorted().collect(Collectors.joining(",")))
                + "|" + policyEpoch + "|" + minAcceptedEpoch;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
